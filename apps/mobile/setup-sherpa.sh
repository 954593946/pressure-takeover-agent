#!/bin/bash
# Download sherpa-onnx AAR and ASR model files for offline speech recognition.
# Required by SherpaVoiceInputProvider.kt
#
# Run once after cloning the repo:
#   cd apps/mobile && bash setup-sherpa.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIBS_DIR="$SCRIPT_DIR/app/libs"
MODELS_DIR="$SCRIPT_DIR/app/src/main/assets/models"

# ── sherpa-onnx AAR (38MB) ────────────────────────────────────────
AAR_FILE="$LIBS_DIR/sherpa-onnx-1.12.11.aar"
if [ ! -f "$AAR_FILE" ]; then
    echo "=== Downloading sherpa-onnx AAR (38MB) ==="
    mkdir -p "$LIBS_DIR"
    curl -L -o "$AAR_FILE" \
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-1.12.11.aar"
    echo "  Done."
else
    echo "  [skip] AAR already present"
fi

# ── Zipformer bilingual zh-en model (190MB INT8) ──────────────────
MODEL_NAME="sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20"
MODEL_DIR="$MODELS_DIR/$MODEL_NAME"
HF_BASE="https://hf-mirror.com/csukuangfj/$MODEL_NAME/resolve/main"

FILES=(
    "encoder-epoch-99-avg-1.int8.onnx"
    "decoder-epoch-99-avg-1.int8.onnx"
    "joiner-epoch-99-avg-1.int8.onnx"
    "tokens.txt"
)

ALL_EXIST=true
for f in "${FILES[@]}"; do
    if [ ! -f "$MODEL_DIR/$f" ]; then
        ALL_EXIST=false
        break
    fi
done

if [ "$ALL_EXIST" = false ]; then
    echo "=== Downloading $MODEL_NAME (190MB total) ==="
    mkdir -p "$MODEL_DIR"
    for f in "${FILES[@]}"; do
        echo "  → $f"
        curl -L --retry 3 -o "$MODEL_DIR/$f" "$HF_BASE/$f"
    done
    echo "  Done."
else
    echo "  [skip] Model files already present"
fi

echo ""
echo "=== Setup complete ==="
du -sh "$LIBS_DIR" "$MODEL_DIR" 2>/dev/null || true
