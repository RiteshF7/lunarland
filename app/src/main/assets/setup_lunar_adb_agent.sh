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

echo "[2/3] Checking lunar-adb-agent directory..."
mkdir -p "${LUNAR_DIR}"
cd "${LUNAR_DIR}"

if [ ! -d ".git" ] && [ -z "$(find . -maxdepth 1 -type f -o -type d ! -name '.' | head -n 1)" ]; then
  echo "[WARN] lunar-adb-agent sources are not present in:"
  echo "  ${LUNAR_DIR}"
  echo
  echo "The Termux bootstrap setup screen should have downloaded the repository"
  echo "automatically. Please re-run the app's initial setup, or pull the repo"
  echo "manually, then run this script again for any shell-side steps."
  exit 1
fi

echo "[3/3] lunar-adb-agent repository is available at:"
echo "  ${LUNAR_DIR}"
echo
echo "You can now follow the README in that repository to run the agent."

