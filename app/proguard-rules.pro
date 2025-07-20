-keep class com.google.firebase.** { *; }

-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

-keep class net.gf.radio24.SplashActivity$** { *; }
-keep class net.gf.radio24.**$** { *; }

-keep class java.net.** { *; }
-keep class javax.net.ssl.** { *; }

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

