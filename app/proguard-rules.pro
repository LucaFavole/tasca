# Room creates AppDb_Impl by reflection from AppDb's name. Room ships this rule too; repeating it here keeps the
# release build working if a library update drops it.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ML Kit builds its components from registrar class names listed in the manifest. R8 full mode keeps those classes
# but drops their constructors, so the document scanner found no SharedPrefManager and crashed on open.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
