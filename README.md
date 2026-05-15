# Gemma TFLite Android Demo

Minimal Android app for running Gemma 3 270M IT via TFLite on device.

## Setup

### 1. Export the model

```bash
cd ../gemmademo-litert-export
export HF_TOKEN=your_token
KERAS_BACKEND=tensorflow python export.py
```

### 2. Push model and vocab to device

```bash
adb push gemma3_270m_it_tf.tflite /sdcard/Android/data/com.example.gemmademo/files/
adb push gemma3_270m_it_hf/assets/tokenizer/vocabulary.spm /sdcard/Android/data/com.example.gemmademo/files/
```

### 3. Build

```bash
# Create local.properties if missing
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug
```

### 4. Patch for 16 KB page-size compatibility

Pixel 9 and other Android 16 devices show a warning dialog for prebuilt `.so` files with 4 KB alignment. Run the post-build patch script to fix ELF alignment and re-sign:

```bash
python3 patch_16kb.py
```

### 5. Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Requirements

- Android device: ARM64, API 31+, 8 GB+ RAM
- Android Studio / Gradle wrapper (included)

## UI

- Prompt input
- Generate button
- Output text with copy and stats
