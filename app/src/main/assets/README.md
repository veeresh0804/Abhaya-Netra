# How to Add Your TFLite Model

## Instructions

1. **Copy your `.tflite` model file** into this `assets` folder
2. **Rename it to `model.tflite`** (or update the filename in MainActivity.kt line 336)

## Model Requirements

Your model should have the following specifications:

- **Input**: 224x224x3 RGB image (normalized to 0.0-1.0 range)
- **Output**: Single float value between 0.0-1.0
  - 0.0 = Real/Authentic
  - 1.0 = Deepfake/Manipulated

## Current Location

Place your model file here:
```
Abhaya-Netra/app/src/main/assets/model.tflite
```

## After Adding the Model

1. Rebuild the project
2. Run the app
3. Check logcat for: "TensorFlow Lite model loaded successfully 🎯"

---

**Note**: If your model has different input/output specifications, you'll need to update the preprocessing code in `MainActivity.kt` (lines 599-712).
