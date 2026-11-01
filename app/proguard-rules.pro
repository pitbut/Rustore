# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class uz.robutpit.znatoki.**$$serializer { *; }
-keepclassmembers class uz.robutpit.znatoki.** { *** Companion; }
-keepclasseswithmembers class uz.robutpit.znatoki.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Socket.IO / Engine.IO
-keep class io.socket.** { *; }
-dontwarn io.socket.**

# LiveKit + WebRTC
-keep class io.livekit.android.** { *; }
-keep class livekit.** { *; }
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-dontwarn io.livekit.**
-dontwarn com.google.protobuf.**
-keep class com.google.protobuf.** { *; }

# Не логировать в релизе
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Сгенерировано R8 (missing_rules.txt): аннотации/опциональные зависимости
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Credential Manager / вход через Google
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
