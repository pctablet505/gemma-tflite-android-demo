# Gemma TFLite Android Demo

Minimal Android app for running Gemma 3 270M IT via TFLite on device.

## Prerequisites

- Android device: ARM64, API 31+, 8 GB+ RAM
- Android SDK with build-tools 36+ (for `apksigner`)
- `adb` in your PATH, or use the full path to `~/Android/Sdk/platform-tools/adb`
- Python 3 (for the post-build patch script)

## Setup

### 1. Export the model

From the export repo:
```bash
cd ../gemmademo-litert-export
export HF_TOKEN=your_huggingface_token
KERAS_BACKEND=tensorflow .venv/bin/python export.py
```

This produces `gemma3_270m_it_tf.tflite` (~1 GB) and `gemma3_270m_it_hf/assets/tokenizer/vocabulary.spm`.

### 2. Configure Android SDK

```bash
# Set your actual Android SDK path
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

### 3. Build

```bash
./gradlew assembleDebug
```

### 4. Patch for 16 KB page-size compatibility

Pixel 9 and other Android 16 devices show a warning dialog for prebuilt `.so` files with 4 KB alignment. Run the post-build patch script to fix ELF alignment and re-sign:

```bash
python3 patch_16kb.py
```

The script finds `apksigner` automatically if your Android SDK is at `~/Android/Sdk` or `ANDROID_HOME` is set.

### 5. Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 6. Push model and vocab to device

The app reads the model and tokenizer from its external files directory.
Create the directory (Android only creates it after first launch) and push:

```bash
adb shell mkdir -p /sdcard/Android/data/com.example.gemmademo/files/
adb push gemma3_270m_it_tf.tflite /sdcard/Android/data/com.example.gemmademo/files/
adb push gemma3_270m_it_hf/assets/tokenizer/vocabulary.spm /sdcard/Android/data/com.example.gemmademo/files/
```

If you reinstall the app later, this directory is wiped — repeat this step.

### 7. Launch

Open the app from the launcher, type a prompt, and tap **Generate**.

First generation takes ~5–10 s to load the model into RAM.

## Troubleshooting

| Issue | Fix |
|---|---|
| "vocabulary.spm not found" | Push vocab to external files dir (step 6) |
| "Model not found" | Push `.tflite` to external files dir (step 6) |
| 16 KB page-size warning dialog | Run `python3 patch_16kb.py` after every `./gradlew assembleDebug` |
| App reinstall wiped files | External files dir is deleted on uninstall — repeat step 6 |
| `adb: command not found` | Add `~/Android/Sdk/platform-tools` to your PATH |

## UI

- Prompt input
- Generate button
- Output text with copy and stats
