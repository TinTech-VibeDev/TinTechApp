-keep class ir.tintech.film.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
