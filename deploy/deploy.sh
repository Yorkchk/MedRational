#!/usr/bin/env bash
# Deploys one image tag on this server (prod or uat). setup-server.sh installs it as
# /usr/local/bin/medrational-deploy and the GitHub Deploy workflow runs it over SSH.
#
#   GitHub: the CI key's forced command (authorized_keys) runs this script whatever the client asks,
#           and the requested command (the tag) arrives in SSH_ORIGINAL_COMMAND:
#             tar -C deploy -cf - compose.yml Caddyfile | ssh deploy@host sha-<full commit>
#   By hand, as deploy on the server:
#             medrational-deploy sha-<full commit>             # keeps compose.yml and Caddyfile
#             medrational-deploy sha-<full commit> < files.tar # also updates them
#
# It only touches compose.yml, Caddyfile and IMAGE_TAG in .env; the secrets (.env.<APP_ENV>, the
# Firebase key) are never read or changed. When the new version doesn't become healthy, the previous
# files and tag are put back, so the server keeps running what it ran before.
set -euo pipefail

DEPLOY_FILES=(compose.yml Caddyfile)

# Globals, so the EXIT trap and rollback still see them once main has returned
STAGING=""
BACKUP=""
PREVIOUS_TAG=""
CADDY_CHANGED=false

log() { printf '[deploy] %s\n' "$*"; }
fail() { printf '[deploy] ERROR: %s\n' "$*" >&2; exit 1; }

# Only immutable tags from publish-image.yml: never latest, so prod runs exactly what uat tested.
# This is also all that's accepted from SSH, so nothing else can reach the shell.
valid_tag() { [[ "${1-}" =~ ^sha-[0-9a-f]{40}$ ]]; }

# The tag from the forced command (SSH_ORIGINAL_COMMAND) or from the command line
requested_tag() {
    if [[ -n "${SSH_ORIGINAL_COMMAND-}" ]]; then
        printf '%s' "$SSH_ORIGINAL_COMMAND"
    else
        printf '%s' "${1-}"
    fi
}

# Sets IMAGE_TAG in the given .env, adding the line when it's missing. The tag is validated first,
# so it's safe in the sed expression.
set_image_tag() {
    local env_file=$1 tag=$2
    valid_tag "$tag" || fail "refusing tag '$tag'"
    if grep -q '^IMAGE_TAG=' "$env_file"; then
        sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$tag/" "$env_file"
    else
        printf 'IMAGE_TAG=%s\n' "$tag" >> "$env_file"
    fi
}

# The last value of KEY in a .env file, empty when it isn't set
env_value() { sed -n "s/^$2=//p" "$1" | tail -n 1; }

# Writes over the file instead of replacing it: compose.yml mounts the Caddyfile as a single file,
# and a renamed file would leave Caddy reading the old one (bind mounts follow the inode)
overwrite() { cat "$1" > "$2"; }

# Unpacks compose.yml and Caddyfile from the tar on stdin, if one was sent. Prints the directory,
# or nothing when stdin is a terminal or empty.
stage_files() {
    local staging tarball
    if [[ -t 0 ]]; then
        return 0
    fi
    staging=$(mktemp -d)
    tarball="$staging/files.tar"
    cat > "$tarball"
    if [[ ! -s "$tarball" ]]; then
        rm -rf "$staging"
        return 0
    fi
    # Only these two names are extracted, so a crafted archive can't write anywhere else
    if ! tar -xf "$tarball" -C "$staging" --no-same-owner --no-same-permissions "${DEPLOY_FILES[@]}"; then
        rm -rf "$staging"
        fail "the archive must contain ${DEPLOY_FILES[*]}"
    fi
    printf '%s' "$staging"
}

compose() { docker compose "$@"; }

api_health() {
    local id
    id=$(compose ps -q api 2> /dev/null) || true
    if [[ -z "$id" ]]; then
        echo missing
        return 0
    fi
    docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id" 2> /dev/null \
        || echo missing
}

# A healthy container (liveness, checked inside it), then the same check through Caddy and HTTPS
wait_until_healthy() {
    # Spring + LibreOffice start in about a minute; the healthcheck's start_period is 90 s
    local domain=$1 timeout=${HEALTH_TIMEOUT:-240} status deadline
    deadline=$((SECONDS + timeout))
    log "waiting for the api to be healthy (up to ${timeout}s)"
    while true; do
        status=$(api_health)
        if [[ "$status" == healthy ]]; then
            break
        elif [[ "$status" == unhealthy ]]; then
            log "api is unhealthy"
            return 1
        elif (( SECONDS >= deadline )); then
            log "api still '$status' after ${timeout}s"
            return 1
        fi
        sleep 5
    done
    log "api is healthy, checking https://$domain"
    for _ in 1 2 3 4 5 6; do
        if curl -fsS --max-time 10 "https://$domain/actuator/health/liveness" > /dev/null; then
            return 0
        fi
        sleep 5
    done
    log "https://$domain/actuator/health/liveness doesn't answer"
    return 1
}

reload_caddy() {
    log "reloading Caddy"
    compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile
}

cleanup() { rm -rf "$BACKUP" "$STAGING"; }

rollback() {
    local file
    log "rolling back to ${PREVIOUS_TAG:-the previous files}"
    overwrite "$BACKUP/.env" .env
    for file in "${DEPLOY_FILES[@]}"; do
        if [[ -f "$BACKUP/$file" ]]; then
            overwrite "$BACKUP/$file" "$file"
        fi
    done
    # On a first deploy there was nothing running to go back to
    if [[ -n "$PREVIOUS_TAG" ]]; then
        compose up -d --remove-orphans || true
        if [[ "$CADDY_CHANGED" == true ]]; then
            reload_caddy || true
        fi
    fi
}

main() {
    local tag app_env domain file app_dir=${MEDRATIONAL_DIR:-/opt/medrational}
    tag=$(requested_tag "$@")
    valid_tag "$tag" || fail "usage: medrational-deploy sha-<full commit hash> (got '$tag')"

    cd "$app_dir" || fail "$app_dir doesn't exist (run setup-server.sh)"
    [[ -f .env ]] || fail "$app_dir/.env is missing (copy deploy/.env.example and fill it in)"
    app_env=$(env_value .env APP_ENV)
    [[ -f ".env.$app_env" ]] || fail "$app_dir/.env.$app_env is missing"
    domain=$(env_value .env DOMAIN)
    [[ -n "$domain" ]] || fail "DOMAIN is not set in $app_dir/.env"

    # Two deploys never run at the same time: the second waits for the first
    exec 9> .deploy.lock
    flock -w 600 9 || fail "another deploy has been running for 10 minutes"

    trap cleanup EXIT
    STAGING=$(stage_files)
    PREVIOUS_TAG=$(env_value .env IMAGE_TAG)
    log "deploying $tag to $app_env ($domain), replacing ${PREVIOUS_TAG:-nothing}"

    # Everything needed to put the server back as it was
    BACKUP=$(mktemp -d)
    cp .env "$BACKUP/.env"
    for file in "${DEPLOY_FILES[@]}"; do
        if [[ -f "$file" ]]; then
            cp "$file" "$BACKUP/$file"
        fi
    done

    if [[ -n "$STAGING" ]]; then
        for file in "${DEPLOY_FILES[@]}"; do
            if ! cmp -s "$STAGING/$file" "$file"; then
                log "updating $file"
                if [[ "$file" == Caddyfile ]]; then
                    CADDY_CHANGED=true
                fi
                overwrite "$STAGING/$file" "$file"
            fi
        done
    fi
    set_image_tag .env "$tag"

    if ! compose config --quiet; then
        rollback
        fail "compose.yml or .env is invalid; nothing was restarted"
    fi
    log "pulling the image"
    if ! compose pull --quiet; then
        rollback
        fail "couldn't pull the image (is docker logged in to ghcr.io?); nothing was restarted"
    fi

    log "starting the containers"
    if compose up -d --remove-orphans \
        && { [[ "$CADDY_CHANGED" == false ]] || reload_caddy; } \
        && wait_until_healthy "$domain"; then
        # Images unused for a week; the server's disk would fill up otherwise
        docker image prune -af --filter until=168h > /dev/null || true
        log "done: $app_env runs $tag"
        return 0
    fi

    log "last api logs:"
    compose logs --tail 100 api || true
    rollback
    fail "$tag didn't become healthy; the server is back on ${PREVIOUS_TAG:-the previous files}"
}

# Sourcing the file (test-deploy.sh) only defines the functions
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
