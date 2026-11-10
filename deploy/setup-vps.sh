#!/bin/sh
# One-time provisioning of a Debian 12 VPS. Run as root (or with sudo).
# Usage: setup-vps.sh [domain] [deploy-public-key]
#   domain: without one the game is served over HTTP on port 80
#   deploy-public-key: SSH key authorized for the restricted `gow-deploy` user (used by CI)
set -eu
SITE="${1:-:80}"
DEPLOY_KEY="${2:-}"

apt-get update
apt-get install -y openjdk-17-jre-headless caddy

id gow >/dev/null 2>&1 || useradd --system --home-dir /opt/gow --shell /usr/sbin/nologin gow
install -d -o gow -g gow /opt/gow

# Restricted user for automated deploys: may only replace the jar and restart the service
id gow-deploy >/dev/null 2>&1 || useradd --create-home --shell /bin/sh gow-deploy
cat > /etc/sudoers.d/gow-deploy <<'SUDOERS'
gow-deploy ALL=(root) NOPASSWD: /usr/bin/install -o gow -g gow -m 644 /home/gow-deploy/gow-server.jar /opt/gow/gow-server.jar, /usr/bin/systemctl restart gow
SUDOERS
chmod 440 /etc/sudoers.d/gow-deploy
visudo -cf /etc/sudoers.d/gow-deploy
if [ -n "$DEPLOY_KEY" ]; then
  install -d -o gow-deploy -g gow-deploy -m 700 /home/gow-deploy/.ssh
  touch /home/gow-deploy/.ssh/authorized_keys
  grep -qxF "$DEPLOY_KEY" /home/gow-deploy/.ssh/authorized_keys || echo "$DEPLOY_KEY" >> /home/gow-deploy/.ssh/authorized_keys
  chown gow-deploy:gow-deploy /home/gow-deploy/.ssh/authorized_keys
  chmod 600 /home/gow-deploy/.ssh/authorized_keys
fi

install -m 644 "$(dirname "$0")/gow.service" /etc/systemd/system/gow.service
install -m 644 "$(dirname "$0")/Caddyfile" /etc/caddy/Caddyfile
mkdir -p /etc/systemd/system/caddy.service.d
printf '[Service]\nEnvironment=GOW_SITE=%s\n' "$SITE" > /etc/systemd/system/caddy.service.d/gow.conf

systemctl daemon-reload
systemctl enable gow caddy
systemctl restart caddy
