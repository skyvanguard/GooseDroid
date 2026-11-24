# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for debugging crash reports
-keepattributes SourceFile,LineNumberTable

# Hide original source file name in stack traces
-renamesourcefileattribute SourceFile

# Keep the application class and main entry points
-keep public class com.skyvanguard.goozy.GoozyApplication { *; }
-keep public class com.skyvanguard.goozy.MainActivity { *; }
-keep public class com.skyvanguard.goozy.CustomizeActivity { *; }

# Keep Widget and BroadcastReceivers (required for Android system)
-keep public class com.skyvanguard.goozy.PetWidget { *; }
-keep public class com.skyvanguard.goozy.PetNotificationManager$NotificationReceiver { *; }


# Preserve enums (used in PetNeeds.MoodState, GooseTasks, etc.)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# AndroidX — only keep what's needed for reflection
-keep class androidx.core.app.NotificationCompat** { *; }
-keep class androidx.appcompat.widget.** { *; }
-dontwarn androidx.**

# Material Design — keep components referenced in XML layouts
-keep class com.google.android.material.button.MaterialButton { *; }
-keep class com.google.android.material.textfield.TextInputLayout { *; }
-keep class com.google.android.material.textfield.TextInputEditText { *; }
-keep class com.google.android.material.switchmaterial.SwitchMaterial { *; }
-dontwarn com.google.android.material.**

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Optimization settings
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose
