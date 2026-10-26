# Proguard rules for tapLedger
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Database *;
    @androidx.room.Entity *;
}

# BouncyCastle ProGuard rules
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
