# Room creates AppDb_Impl by reflection from AppDb's name. Room ships this rule too; repeating it here keeps the
# release build working if a library update drops it.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
