# ==================== Zaxo Android — ProGuard/R8 Rules ====================
# E.2: Production hardening — R8 rules for all key libraries

# ==================== Model Classes (used by Gson/Firestore) ====================
-keep class com.zaxo.app.model.** { *; }
-keepclassmembers class com.zaxo.app.model.** {
    <fields>;
    <init>(...);
}

# ==================== Gson ====================
# Prevents R8 from stripping interface information from TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
# Prevents R8 from merging Gson type adapters with their enclosing classes
-keep,allowobfuscation,allowshrinking class * implements com.google.gson.TypeAdapterFactory
-keep,allowobfuscation,allowshrinking class * implements com.google.gson.TypeAdapter
# Application specific Gson classes
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# ==================== Room Entities ====================
-keep class com.zaxo.app.data.dao.** { *; }
-keep class com.zaxo.app.data.database.** { *; }
# Room uses reflection to check column info; keep entity fields
-keepclassmembers class * extends androidx.room.Entity {
    <fields>;
}

# ==================== Signal Protocol (E2E Encryption) ====================
-keep class org.signal.libsignal.** { *; }
-keepclassmembers class org.signal.libsignal.** { *; }

# ==================== CameraX ====================
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ==================== Firebase ====================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
# Firestore — keep POJOs used as document snapshots
-keepclassmembers class * {
    *** get*();
    void set*(***);
}

# ==================== Hilt / Dagger ====================
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }

# ==================== Timber ====================
-dontwarn timber.log.Timber

# ==================== Parcelize ====================
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ==================== Kotlin Coroutines ====================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ==================== Compose ====================
-dontwarn androidx.compose.**

# ==================== Media3 / ExoPlayer ====================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ==================== WorkManager ====================
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ==================== WebRTC / LiveKit ====================
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class io.livekit.** { *; }
-dontwarn io.livekit.**

# ==================== Coil ====================
-dontwarn coil.**

# ==================== ML Kit Speech Recognition ====================
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
# ML Kit on-device models must not be stripped
-keep class com.google.android.gms.internal.** { *; }
-dontwarn com.google.android.gms.internal.**

# ==================== Media Session ====================
-keep class android.support.v4.media.** { *; }
-dontwarn android.support.v4.media.**

# ==================== General Android ====================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ==================== Native Methods ====================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ==================== Enum ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== Serializable ====================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
