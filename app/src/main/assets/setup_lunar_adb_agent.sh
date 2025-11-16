#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LUNAR_DIR="${PROJECT_ROOT}/lunar-adb-agent"

echo "[1/5] Creating lunar-adb-agent directory..."
mkdir -p "${LUNAR_DIR}"
cd "${LUNAR_DIR}"

echo "[2/5] Cloning / updating droidrun and adbutils repositories..."
if [ ! -d "droidrun/.git" ]; then
  git clone https://github.com/droidrun/droidrun.git
else
  (cd droidrun && git pull --ff-only || true)
fi

if [ ! -d "adbutils/.git" ]; then
  git clone https://github.com/droidrun/adbutils.git
else
  (cd adbutils && git pull --ff-only || true)
fi

echo "[3/5] Ensuring Python and pip are ready..."
python -m pip install --upgrade pip

echo "[4/5] Installing droidrun with Google Gemini extras (droidrun[google])..."
python -m pip install "droidrun[google]"

echo
echo "[INFO] To use Google Gemini with droidrun, ensure GOOGLE_API_KEY is set, e.g.:"
echo "  export GOOGLE_API_KEY=\"your_gemini_api_key\""
echo

echo "[5/5] Verifying droidrun CLI is available..."
droidrun --help >/dev/null

echo
echo "Droidrun environment for Google Gemini is ready."
echo "From now on you can run (inside this repo):"
echo "  cd \"${LUNAR_DIR}\""
echo "  export GOOGLE_API_KEY=your_key_here"
echo "  droidrun setup   # to install DroidRun Portal and prepare a device"
echo "  droidrun run     # to run natural language commands"



