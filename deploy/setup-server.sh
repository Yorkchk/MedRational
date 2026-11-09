#!/usr/bin/env bash
# Prepares a fresh Ubuntu 24.04 server (Hetzner) to run MedRational, prod or uat. Run it once as
# root, with deploy.sh next to it; running it again is safe and only fixes what drifted.
#
#   scp deploy/setup-server.sh deploy/deploy.sh root@<server-ip>:/root/
#   ssh root@<server-ip> "bash /root/setup-server.sh --ci-key 'ssh-ed25519 AAAA... github-deploy'"
#
# It sets up: the deploy user (with the keys root has, which Hetzner took from the console),
# the GitHub deploy key limited to medrational-deploy, Docker, swap, ufw, fail2ban, automatic
# security updates, /opt/medrational, and last SSH hardening (no root, no passwords). After it,
# log in as deploy@<server-ip>; root can no longer log in over SSH.
#
# Secrets are never handled here: copy .env, .env.<env> and the Firebase key afterwards
# (docs/deployment.md).
set -euo pipefail

CI_KEY=""
SWAP_SIZE="2G"
DEPLOY_USER="deploy"
APP_DIR="/opt/medrational"
DEPLOY_COMMAND="/usr/local/bin/medrational-deploy"
# The image's non-root user (Dockerfile: useradd app), which reads the Firebase key
APP_UID=1001

usage() {
    cat << 'EOF'
Usage: setup-server.sh [--ci-key '<public key>'] [--swap <size>]

  --ci-key  Public key of the GitHub Deploy workflow. It can only run medrational-deploy, never a
            shell. Can be added later by running the script again.
  --swap    Swap file size when the server has none (default 2G).
EOF
}

log() { printf '\n==> %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --ci-key) CI_KEY="${2-}"; shift 2 ;;
        --swap) SWAP_SIZE="${2-}"; shift 2 ;;
        -h | --help) usage; exit 0 ;;
        *) usage >&2; fail "unknown option $1" ;;
    esac
done

[[ $EUID -eq 0 ]] || fail "run as root"
# shellcheck source=/dev/null
. /etc/os-release
[[ "${ID-}" == ubuntu ]] || fail "this script expects Ubuntu (found ${ID-unknown})"
SCRIPT_DIR=$(dirname "$(readlink -f "$0")")
[[ -f "$SCRIPT_DIR/deploy.sh" ]] || fail "copy deploy.sh next to setup-server.sh"

KEY_PATTERN='^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)|sk-ssh-ed25519@openssh\.com|sk-ecdsa-sha2-nistp256@openssh\.com) [A-Za-z0-9+/=]+( .*)?$'
if [[ -n "$CI_KEY" && ! "$CI_KEY" =~ $KEY_PATTERN ]]; then
    fail "--ci-key doesn't look like a public key (the content of github-deploy.pub)"
fi

export DEBIAN_FRONTEND=noninteractive
APT_OPTS=(-yq -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold)

log "Updating packages"
apt-get update -q
apt-get "${APT_OPTS[@]}" upgrade
apt-get "${APT_OPTS[@]}" install ufw fail2ban unattended-upgrades curl ca-certificates

log "Installing Docker"
if ! command -v docker > /dev/null || ! docker compose version > /dev/null 2>&1; then
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update -q
    apt-get "${APT_OPTS[@]}" install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker > /dev/null
docker compose version

log "User $DEPLOY_USER"
if ! id "$DEPLOY_USER" > /dev/null 2>&1; then
    adduser --disabled-password --gecos "" "$DEPLOY_USER"
fi
# The docker group is root-equivalent anyway, so sudo without a password adds no risk, and the
# user has no password to type
usermod -aG sudo,docker "$DEPLOY_USER"
echo "$DEPLOY_USER ALL=(ALL) NOPASSWD:ALL" > "/etc/sudoers.d/90-$DEPLOY_USER"
chmod 440 "/etc/sudoers.d/90-$DEPLOY_USER"
visudo -cqf "/etc/sudoers.d/90-$DEPLOY_USER"

home=$(getent passwd "$DEPLOY_USER" | cut -d: -f6)
auth="$home/.ssh/authorized_keys"
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$home/.ssh"
touch "$auth"

# Your personal keys: the ones root has (Hetzner puts the console's SSH keys there). Lines with
# options are skipped, and keys already there aren't added twice.
if [[ -s /root/.ssh/authorized_keys ]]; then
    while IFS= read -r line; do
        if [[ "$line" =~ $KEY_PATTERN ]] && ! grep -qF "$line" "$auth"; then
            printf '%s\n' "$line" >> "$auth"
        fi
    done < /root/.ssh/authorized_keys
fi

# The GitHub key: whatever it asks, sshd runs medrational-deploy, with no shell, port forwarding
# or terminal. Any older line with the same key is replaced.
if [[ -n "$CI_KEY" ]]; then
    blob=$(awk '{print $2}' <<< "$CI_KEY")
    grep -vF "$blob" "$auth" > "$auth.new" || true
    printf 'restrict,command="%s" %s\n' "$DEPLOY_COMMAND" "$CI_KEY" >> "$auth.new"
    mv "$auth.new" "$auth"
fi
chown "$DEPLOY_USER:$DEPLOY_USER" "$auth"
chmod 600 "$auth"

# Without a personal key, hardening SSH below would lock everyone out
grep -v 'command=' "$auth" | grep -qE "$KEY_PATTERN" \
    || fail "no personal SSH key for $DEPLOY_USER: add yours to /root/.ssh/authorized_keys and run again"

log "Deploy script"
install -m 755 -o root -g root "$SCRIPT_DIR/deploy.sh" "$DEPLOY_COMMAND"
install -d -m 750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR"
# The Firebase key is mounted into the container, whose user must read it
if [[ -f "$APP_DIR/firebase-service-account.json" ]]; then
    chown "$APP_UID:$APP_UID" "$APP_DIR/firebase-service-account.json"
    chmod 400 "$APP_DIR/firebase-service-account.json"
fi

log "Swap"
if [[ -z "$(swapon --show --noheadings)" ]]; then
    fallocate -l "$SWAP_SIZE" /swapfile
    chmod 600 /swapfile
    mkswap /swapfile > /dev/null
    swapon /swapfile
fi
if ! grep -q '^/swapfile ' /etc/fstab && [[ -f /swapfile ]]; then
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
# Swap only under real memory pressure (the JVM and LibreOffice share the RAM)
echo 'vm.swappiness=10' > /etc/sysctl.d/90-medrational.conf
sysctl -q -p /etc/sysctl.d/90-medrational.conf
swapon --show

log "Firewall (ufw)"
# Docker publishes ports outside ufw's rules, so the Hetzner firewall is the real guard for those;
# compose.yml only publishes 80 and 443 anyway (the API stays on Docker's network)
ufw default deny incoming > /dev/null
ufw default allow outgoing > /dev/null
ufw allow 22/tcp > /dev/null
ufw allow 80/tcp > /dev/null
ufw allow 443/tcp > /dev/null
ufw allow 443/udp > /dev/null
ufw --force enable > /dev/null
ufw status

log "fail2ban"
cat > /etc/fail2ban/jail.d/medrational.local << 'EOF'
# Bans an IP for an hour after 5 failed SSH logins
[sshd]
enabled = true
backend = systemd
maxretry = 5
bantime = 1h
EOF
systemctl enable fail2ban > /dev/null
systemctl restart fail2ban

log "Automatic security updates"
cat > /etc/apt/apt.conf.d/20auto-upgrades << 'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
# Kernel and libc updates need a reboot; at 04:00 UTC (Morocco time) few students use the app,
# and the containers start again on their own (restart: unless-stopped)
cat > /etc/apt/apt.conf.d/52medrational-unattended << 'EOF'
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "04:00";
EOF
systemctl enable --now unattended-upgrades > /dev/null

log "SSH hardening"
# Drop-ins are read first and the first value wins, so this beats Ubuntu's and cloud-init's
cat > /etc/ssh/sshd_config.d/10-medrational.conf << 'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
EOF
sshd -t
systemctl reload ssh 2> /dev/null || systemctl restart ssh

ip=$(hostname -I | awk '{print $1}')
cat << EOF

Done. Root can no longer log in over SSH: use $DEPLOY_USER@$ip from now on.

Next steps (docs/deployment.md):
  1. From the backend folder on the laptop, copy the config and secrets:
       scp deploy/.env .env.<prod|uat> firebase-service-account.json $DEPLOY_USER@$ip:$APP_DIR/
     then on the server:
       sudo chown $APP_UID:$APP_UID $APP_DIR/firebase-service-account.json && sudo chmod 400 $APP_DIR/firebase-service-account.json
       chmod 600 $APP_DIR/.env.*
  2. Let Docker pull the private image (a PAT with read:packages):
       echo <token> | docker login ghcr.io -u <github user> --password-stdin
  3. Host key for the GitHub environment's DEPLOY_KNOWN_HOSTS secret, from the laptop:
       ssh-keyscan <domain>
  4. Deploy with the "Deploy" workflow on GitHub, or here: medrational-deploy sha-<full commit> < files.tar
EOF
