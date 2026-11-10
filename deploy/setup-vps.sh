#!/bin/sh
# One-time provisioning of a Debian 12 VPS. Run as root (or with sudo).
# Usage: setup-vps.sh [domain]   (without a domain the game is served over HTTP on port 80)
set -eu
SITE="${1:-:80}"

apt-get update
apt-get install -y openjdk-17-jre-headless caddy

id gow >/dev/null 2>&1 || useradd --system --home-dir /opt/gow --shell /usr/sbin/nologin gow
install -d -o gow -g gow /opt/gow

install -m 644 "$(dirname "$0")/gow.service" /etc/systemd/system/gow.service
install -m 644 "$(dirname "$0")/Caddyfile" /etc/caddy/Caddyfile
mkdir -p /etc/systemd/system/caddy.service.d
printf '[Service]\nEnvironment=GOW_SITE=%s\n' "$SITE" > /etc/systemd/system/caddy.service.d/gow.conf

systemctl daemon-reload
systemctl enable gow caddy
systemctl restart caddy
