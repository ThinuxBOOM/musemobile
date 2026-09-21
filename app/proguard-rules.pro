-keep class com.musemobile.app.bridge.SpotifyBridge { *; }
-keep class com.musemobile.app.webview.SpotifyWebViewClient { *; }
-keep class com.musemobile.app.webview.SpotifyWebChromeClient { *; }
-keep class com.musemobile.app.webview.injections.** { *; }
-keep class com.musemobile.app.webview.helpers.** { *; }
-keep class com.musemobile.app.service.MediaNotificationService { *; }
-keep class com.musemobile.app.proxy.LocalProxyManager { *; }
-keep class com.musemobile.app.ui.SplashActivity { *; }
-keep class com.musemobile.app.ui.MainActivity { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.annotation.concurrent.GuardedBy
-keepclassmembers enum * { *; }
-keepclassmembers class * implements java.io.Serializable { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepattributes *Annotation*,JavascriptInterface,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.google.protos.** { *; }
-keep class ** extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class ** extends com.google.protobuf.GeneratedMessage { *; }
-keepclassmembers class ** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keepclassmembers class ** extends com.google.protobuf.GeneratedMessage {
    <fields>;
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
