# Optimization & Obfuscation configuration for Athan Alarm

# Preserve line numbers and file names for crash reporting in Google Play Console
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Google Cast SDK
# CastOptionsProvider is instantiated via reflection by Google Play Services
-keep class com.example.cast.CastOptionsProvider {
    public <init>();
    *;
}
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.cast.framework.** { *; }
-keep interface com.google.android.gms.cast.framework.** { *; }

# AndroidX MediaRouter
-keep class androidx.mediarouter.** { *; }

# NanoHTTPD (LocalHttpAudioServer)
-keep class fi.iki.elonen.** { *; }
-keepclassmembers class fi.iki.elonen.NanoHTTPD* { *; }
-keep class com.example.cast.LocalHttpAudioServer** { *; }

# Models, Enums & Data Classes (Prevent reflection & serialization issues)
-keep class com.example.model.** { *; }
-keepclassmembers enum com.example.model.** { *; }

# Moshi & Retrofit
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.squareup.moshi.** { *; }

# App Widgets
-keep class com.example.widget.** extends android.appwidget.AppWidgetProvider { *; }

# Receivers, Services & Application referenced in AndroidManifest
-keep class com.example.AthanApplication { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.alarm.AthanAlarmReceiver { *; }
-keep class com.example.alarm.BootReceiver { *; }
-keep class com.example.alarm.AthanAudioService { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose Runtime
-keepclassmembers class androidx.compose.runtime.RecomposeScopeImpl { *; }
