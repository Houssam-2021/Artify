# Artify keeps no reflection-based serialization; the document format is
# hand-written binary, so default R8 rules are sufficient.

# Strip verbose/debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
