#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LUNAR_DIR="${PROJECT_ROOT}/lunar-adb-agent"

CLEAN_CLONE=false
for arg in "$@"; do
  if [ "$arg" = "--clean" ]; then
    CLEAN_CLONE=true
  fi
done

if [ "$CLEAN_CLONE" = true ] && [ -d "${LUNAR_DIR}" ]; then
  echo "[1/3] --clean passed, removing existing lunar-adb-agent directory..."
  rm -rf "${LUNAR_DIR}"
fi

echo "[2/3] Creating lunar-adb-agent directory..."
mkdir -p "${LUNAR_DIR}"
cd "${LUNAR_DIR}"

echo "[3/3] Cloning / updating lunar-adb-agent repository..."
if [ ! -d ".git" ]; then
  git clone https://github.com/RiteshF7/lunar-adb-agent.git .
else
  git pull --ff-only || true
fi

echo
echo "lunar-adb-agent repository is ready in:"
echo "  ${LUNAR_DIR}"
echo
echo "You can now follow the README in that repository to run the agent."

