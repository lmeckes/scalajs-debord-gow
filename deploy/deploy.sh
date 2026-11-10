#!/bin/sh
# Builds the fat jar and deploys it to the VPS.
# Usage: deploy/deploy.sh user@host   (user: `gow-deploy`, or any user with sudo)
set -eu
TARGET="${1:?usage: deploy.sh user@host}"
cd "$(dirname "$0")/.."

sbt -batch server/assembly
scp gow-server/target/scala-2.13/gow-server.jar "$TARGET:gow-server.jar"
ssh "$TARGET" 'sudo /usr/bin/install -o gow -g gow -m 644 "$HOME/gow-server.jar" /opt/gow/gow-server.jar && rm "$HOME/gow-server.jar" && sudo /usr/bin/systemctl restart gow && sleep 5 && systemctl is-active gow'
