# Offline app — keep rules minimal; enable R8 shrinking in release only when tested.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.google.mlkit.** { *; }
