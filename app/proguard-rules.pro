# --- JS bridge: WebView calls these by name via reflection ---
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.musemobile.app.bridge.SpotifyBridge { *; }
-keepattributes JavascriptInterface,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- App classes reached from the manifest / WebView (small, keep; cost is negligible) ---
-keep class com.musemobile.app.webview.SpotifyWebViewClient { *; }
-keep class com.musemobile.app.webview.SpotifyWebChromeClient { *; }
-keep class com.musemobile.app.webview.injections.** { *; }
-keep class com.musemobile.app.webview.helpers.** { *; }
-keep class com.musemobile.app.service.MediaNotificationService { *; }
-keep class com.musemobile.app.proxy.LocalProxyManager { *; }
-keep class com.musemobile.app.ui.SplashActivity { *; }
-keep class com.musemobile.app.ui.MainActivity { *; }
-keep class com.musemobile.app.ui.OfflineActivity { *; }
-keep class com.musemobile.app.service.OfflineMediaService { *; }
-keep class com.musemobile.app.service.DownloadService { *; }

# --- BouncyCastle: only the cert-builder APIs LocalProxyManager actually uses ---
-keep class org.bouncycastle.asn1.x500.** { *; }
-keep class org.bouncycastle.asn1.x509.** { *; }
-keep class org.bouncycastle.cert.jcajce.** { *; }
-keep class org.bouncycastle.operator.jcajce.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.annotation.concurrent.GuardedBy

# --- kotlinx.serialization: keep generated serializers for InnerTube models ---
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep class com.musemobile.app.innertube.models.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}

# --- Release: strip verbose/debug/info/warn logs (errors still go to Crashlytics) ---
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** println(...);
}

# NewPipe / Rhino - java.beans.* not available on Android on device
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.script.**
# do not add -keep class org.mozilla.javascript.** - it pulls in classes
# referencing java.beans.* which breaks ART install-time verification
