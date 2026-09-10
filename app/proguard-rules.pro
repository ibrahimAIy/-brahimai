-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
