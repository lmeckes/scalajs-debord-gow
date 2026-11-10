#!/bin/sh
# Builds the fat jar and deploys it to the VPS.
# Usage: deploy/deploy.sh user@host   (the user needs sudo)
set -eu
TARGET="${1:?usage: deploy.sh user@host}"
cd "$(dirname "$0")/.."

sbt -batch server/assembly
scp gow-server/target/scala-2.13/gow-server.jar "$TARGET:/tmp/gow-server.jar"
ssh "$TARGET" 'sudo install -o gow -g gow -m 644 /tmp/gow-server.jar /opt/gow/gow-server.jar && rm /tmp/gow-server.jar && sudo systemctl restart gow && sleep 5 && systemctl is-active gow'
