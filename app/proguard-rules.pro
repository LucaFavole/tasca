# Room creates AppDb_Impl by reflection from AppDb's name, and the bundled SQLite library registers its JNI
# methods by class and method name. Room and the driver ship these rules too; repeating them here keeps the
# release build working if a library update drops them.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepclasseswithmembers class androidx.sqlite.driver.bundled.** { native <methods>; }
