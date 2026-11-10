# ASTRA release rules. Libraries (Firebase, WorkManager, Credentials, Compose) ship their own consumer rules.
# The app has no reflection-based JSON: all codecs are hand-written in :core.

# Keep line numbers in crash reports while hiding source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Enums are looked up by name in the codecs (valueOf / entries).
-keepclassmembers enum app.astra.** { *; }

# WorkManager creates workers by class name.
-keep class app.astra.system.DailyNoteWorker { <init>(...); }

# Credential Manager / Google ID token (recommended by androidx.credentials).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# Remove verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
