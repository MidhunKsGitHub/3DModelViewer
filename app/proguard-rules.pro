# Filament / gltfio
-keep class com.google.android.filament.** { *; }
-keep class com.google.android.filament.gltfio.** { *; }
-keep class com.google.android.filament.utils.** { *; }
-keepclassmembers class com.google.android.filament.** { *; }

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
