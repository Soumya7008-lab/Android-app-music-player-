# Add project-specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# Aggressive optimizations
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove logs
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Preserve Compose and Media3 essentials
-keep class androidx.compose.** { *; }
-keep class androidx.media3.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
