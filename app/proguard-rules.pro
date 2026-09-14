# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.jsoup.select.Elements *;
}
