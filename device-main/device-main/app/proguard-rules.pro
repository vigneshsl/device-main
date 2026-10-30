# ProGuard / R8 rules for Smart Network Alert
# Keep DataStore Preferences internal classes safe during R8 shrinking
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Remove verbose or debug log calls in release builds for strict zero-logging privacy
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
