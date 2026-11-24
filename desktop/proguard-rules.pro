# Desktop release packaging (Compose / ProGuard). Suppress optional JVM/Android-only
# refs from OkHttp, vlcj, and Coil; keep entrypoint and Coil SPI loaders.

-keepclasseswithmembers public class com.mab.f2mbox.desktop.MainKt {
    public static void main(java.lang.String[]);
}

-keep class * extends coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * extends coil3.util.FetcherServiceLoaderTarget { *; }

-dontwarn android.**
-dontwarn androidx.**
-dontwarn dalvik.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.jsoup.helper.RequestAuthHandler
-dontwarn com.sun.awt.**
-dontwarn uk.co.caprica.vlcj.**