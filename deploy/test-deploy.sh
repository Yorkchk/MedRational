#!/usr/bin/env bash
# Tests for deploy.sh's input handling, run by Backend CI: the tag is the only thing the GitHub key
# can send to the server, so anything but sha-<full commit> must be refused before reaching docker.
#   bash deploy/test-deploy.sh
set -uo pipefail

# shellcheck source=deploy/deploy.sh
source "$(dirname "$0")/deploy.sh"
set +e   # deploy.sh turns errexit on; the tests check failures themselves

failures=0
check() {
    local name=$1 expected=$2 actual=$3
    if [[ "$expected" == "$actual" ]]; then
        printf 'ok   %s\n' "$name"
    else
        printf 'FAIL %s: expected [%s], got [%s]\n' "$name" "$expected" "$actual"
        failures=$((failures + 1))
    fi
}
status_of() { "$@" > /dev/null 2>&1; echo $?; }

sha=0123456789abcdef0123456789abcdef01234567

# valid_tag
check "full sha tag" 0 "$(status_of valid_tag "sha-$sha")"
check "latest" 1 "$(status_of valid_tag latest)"
check "short sha" 1 "$(status_of valid_tag sha-0123456)"
check "uppercase sha" 1 "$(status_of valid_tag "sha-${sha^^}")"
check "empty" 1 "$(status_of valid_tag "")"
check "command injection" 1 "$(status_of valid_tag "sha-$sha; rm -rf /")"
check "trailing newline" 1 "$(status_of valid_tag "sha-$sha"$'\n'"id")"
check "extra argument" 1 "$(status_of valid_tag "sha-$sha --force")"

# requested_tag: the forced command's SSH_ORIGINAL_COMMAND wins over the arguments
check "tag from argument" "sha-$sha" "$(unset SSH_ORIGINAL_COMMAND; requested_tag "sha-$sha")"
check "tag from ssh" "sha-$sha" "$(SSH_ORIGINAL_COMMAND="sha-$sha" requested_tag ignored)"
check "nothing" "" "$(unset SSH_ORIGINAL_COMMAND; requested_tag)"

# set_image_tag only changes IMAGE_TAG
dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT
printf 'APP_ENV=uat\nDOMAIN=uat.swisra.app\nIMAGE_TAG=sha-old\n' > "$dir/.env"
set_image_tag "$dir/.env" "sha-$sha"
check "tag replaced" "APP_ENV=uat|DOMAIN=uat.swisra.app|IMAGE_TAG=sha-$sha" "$(paste -sd'|' "$dir/.env")"

printf 'APP_ENV=uat\nDOMAIN=uat.swisra.app\n' > "$dir/.env"
set_image_tag "$dir/.env" "sha-$sha"
check "tag added" "IMAGE_TAG=sha-$sha" "$(tail -n 1 "$dir/.env")"

printf 'IMAGE_TAG=sha-old\n' > "$dir/.env"
check "invalid tag refused" 1 "$( (set_image_tag "$dir/.env" "sha-x/;s/.*/pwned/") > /dev/null 2>&1; echo $?)"
check "env untouched after refusal" "IMAGE_TAG=sha-old" "$(cat "$dir/.env")"

# env_value
printf 'DOMAIN=a.example\n# DOMAIN=commented\nDOMAIN=b.example\n' > "$dir/.env"
check "last value wins" "b.example" "$(env_value "$dir/.env" DOMAIN)"
check "missing key" "" "$(env_value "$dir/.env" APP_ENV)"

# stage_files extracts only compose.yml and Caddyfile
src=$(mktemp -d)
printf 'services: {}\n' > "$src/compose.yml"
printf ':80\n' > "$src/Caddyfile"
printf 'evil\n' > "$src/evil.sh"
staged=$(tar -C "$src" -cf - compose.yml Caddyfile evil.sh | stage_files)
check "staged files" "Caddyfile compose.yml files.tar" "$(find "$staged" -mindepth 1 -printf '%f\n' | sort | paste -sd' ')"
check "nothing staged from empty stdin" "" "$(stage_files < /dev/null)"
check "archive without the files refused" 1 "$( (tar -C "$src" -cf - evil.sh | stage_files) > /dev/null 2>&1; echo $?)"
rm -rf "$staged"

# main, end to end, with fake docker and curl on the PATH. FAKE_HEALTH is what the api's
# healthcheck reports; every docker call is recorded in calls.log.
bin=$(mktemp -d)
cat > "$bin/docker" << 'EOF'
#!/usr/bin/env bash
echo "docker $*" >> "$FAKE_LOG"
case "$*" in
    "compose ps -q api") echo fake-api-id ;;
    inspect*) echo "$FAKE_HEALTH" ;;
esac
EOF
printf '#!/usr/bin/env bash\nexit 0\n' > "$bin/curl"
chmod +x "$bin/docker" "$bin/curl"

new_server() {
    app=$(mktemp -d)
    printf 'APP_ENV=uat\nDOMAIN=uat.swisra.app\nIMAGE_TAG=sha-%s\n' "$old" > "$app/.env"
    printf 'SECRET=1\n' > "$app/.env.uat"
    printf 'services: {old: {}}\n' > "$app/compose.yml"
    printf ':80 # old\n' > "$app/Caddyfile"
    : > "$app/calls.log"
}
run_main() {
    (PATH="$bin:$PATH" MEDRATIONAL_DIR="$app" FAKE_LOG="$app/calls.log" FAKE_HEALTH=$1 HEALTH_TIMEOUT=5 \
        main "sha-$sha") < "$2" > /dev/null 2>&1
    echo $?
}
old=fedcba9876543210fedcba9876543210fedcba98
printf 'services: {new: {}}\n' > "$src/compose.yml"
tar -C "$src" -cf "$src/files.tar" compose.yml Caddyfile

new_server
check "deploy succeeds" 0 "$(run_main healthy "$src/files.tar")"
check "deploy sets the tag" "sha-$sha" "$(env_value "$app/.env" IMAGE_TAG)"
check "deploy updates compose.yml" "services: {new: {}}" "$(cat "$app/compose.yml")"
check "deploy reloads Caddy when the Caddyfile changed" 1 "$(grep -c 'caddy reload' "$app/calls.log")"
check "deploy keeps the secrets" "SECRET=1" "$(cat "$app/.env.uat")"
rm -rf "$app"

new_server
check "unhealthy deploy fails" 1 "$(run_main unhealthy "$src/files.tar")"
check "rollback restores the tag" "sha-$old" "$(env_value "$app/.env" IMAGE_TAG)"
check "rollback restores compose.yml" "services: {old: {}}" "$(cat "$app/compose.yml")"
check "rollback restores the Caddyfile" ":80 # old" "$(cat "$app/Caddyfile")"
check "rollback restarts the old version" 2 "$(grep -c 'compose up -d' "$app/calls.log")"
rm -rf "$app"

new_server
check "bad tag refused before docker" 1 "$( (PATH="$bin:$PATH" MEDRATIONAL_DIR="$app" FAKE_LOG="$app/calls.log" \
    SSH_ORIGINAL_COMMAND="latest" main) < /dev/null > /dev/null 2>&1; echo $?)"
check "bad tag runs no docker command" 0 "$(wc -l < "$app/calls.log")"
rm -rf "$app" "$src" "$bin"

if (( failures > 0 )); then
    echo "$failures test(s) failed"
    exit 1
fi
echo "all deploy.sh tests passed"
