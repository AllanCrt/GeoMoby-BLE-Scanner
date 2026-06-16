# Proguard rules for BLE Scanner
# Keep iBeacon parser (uses reflection-free ByteBuffer parsing, but safety first)
-keep class com.geomoby.blescanner.domain.model.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
