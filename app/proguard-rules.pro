# ================= MiMes Mix ProGuard/R8 rules =================

# Строки для чтения стектрейсов
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ================= Hilt / Dagger =================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**
-keep class * extends androidx.lifecycle.ViewModel

# ================= Firebase / Play Services =================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ================= WebRTC =================
# org.webrtc использует JNI и reflection
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { native <methods>; }
-dontwarn org.webrtc.**

# ================= Модели данных (Firestore mapping) =================
-keep class com.mimes.app.data.** { *; }
-keep class com.mimes.app.ui.chat.Chat { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.ServerTimestamp <fields>;
}

# ================= Retrofit/OkHttp (не используется, но на будущее) =================
-dontwarn okhttp3.**
-dontwarn okio.**

# ================= Coil =================
-dontwarn coil.**

# ================= Coroutines =================
-dontwarn kotlinx.coroutines.**

# ================= Compose =================
-dontwarn androidx.compose.**
