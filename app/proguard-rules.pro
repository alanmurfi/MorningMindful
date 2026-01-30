# MorningMindful ProGuard Rules

# ============================================
# Security: Strip debug logs in release builds
# ============================================
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ============================================
# Keep Room database entities and DAOs
# ============================================
-keep class com.morningmindful.data.entity.** { *; }
-keep class com.morningmindful.data.dao.** { *; }

# ============================================
# Keep Kotlin Serialization (if used)
# ============================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ============================================
# Keep Google AdMob
# ============================================
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# ============================================
# Keep Security & Encryption classes
# ============================================
-keep class androidx.security.crypto.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }

# ============================================
# General Android rules
# ============================================
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep ViewBinding classes
-keep class * implements androidx.viewbinding.ViewBinding {
    public static ** inflate(android.view.LayoutInflater);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============================================
# Coroutines
# ============================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============================================
# Firebase Crashlytics
# ============================================
-keep class com.google.firebase.crashlytics.** { *; }
-keep class com.google.firebase.** { *; }
-keep public class * extends java.lang.Exception
