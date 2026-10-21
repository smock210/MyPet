# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ==== VK SDK (com.vk:android-sdk-core/api 4.x) ====
-keep class com.vk.api.sdk.** { *; }
-dontwarn com.vk.api.sdk.**

# ==== Gson (utils/DataExporter: поля data-классов экспорта) ====
-keep class com.smock210.mypet.sqlClass.** { *; }
-keep class com.smock210.mypet.utils.DataExporter$ExportData { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn com.google.gson.**

# ==== Room (аннотации обрабатываются kapt, но entity сохраняют имена таблиц) ====
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ==== Picasso ====
-dontwarn com.squareup.picasso.**

# Сохраняем номера строк для читаемых крашей
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
