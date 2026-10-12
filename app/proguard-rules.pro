# Proguard rules for tapLedger
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Database *;
    @androidx.room.Entity *;
}

# PDFBox Android & BouncyCastle ProGuard rules
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
