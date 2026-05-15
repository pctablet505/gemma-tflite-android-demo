# Gemma TFLite Android Demo

Minimal Android app for running Gemma 3 270M via TFLite on device.

## Setup

1. Export the model:
```bash
cd ../gemmademo-litert-export
export HF_TOKEN=your_token
KERAS_BACKEND=tensorflow python export.py
```

2. Push to device:
```bash
adb push gemma3_270m_tf.tflite /sdcard/Android/data/com.example.gemmademo/files/
adb push gemma3_270m_hf/assets/tokenizer/vocabulary.spm /sdcard/Android/data/com.example.gemmademo/files/
```

3. Set Android SDK path in `local.properties` (create if missing):
```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
```

4. Build and install:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Requirements

- Android device: ARM64, API 31+, 8 GB+ RAM
- Android Studio / Gradle wrapper (included)

## UI

- Prompt input
- Generate button
- Output text with copy and stats
