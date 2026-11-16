#!/usr/bin/env bash
# Builds the Taskapp uber-jar and installs it for the current user (spec §6). Run it again to upgrade.
set -euo pipefail
cd "$(dirname "$0")"

mvn -q -DskipTests package

install -D -m 644 app-server/target/app-server-runner.jar "$HOME/.local/share/taskapp/taskapp.jar"
install -D -m 755 bin/taskapp "$HOME/.local/bin/taskapp"

echo "Installed. Start it with: taskapp  (then open http://127.0.0.1:7070)"
