# Add project specific ProGuard rules here.
# Keep SQLCipher JNI entry points
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.zetetic.database.** { *; }
-dontwarn org.bouncycastle.**
