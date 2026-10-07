# Release keep rules (audited in Phase 2, task 2.9).
# Covered without extra rules here:
# - Play Billing, DataStore, Coil, AboutLibraries and Navigation ship consumer rules in their AARs.
#   AboutLibraries reads R.raw.aboutlibraries by direct reference, so resource shrinking keeps it.
# - Enums are parsed by their own id fields (QuizMode.fromId, CategoryGroup.fromId,
#   QuizCategory.fromRoute), never by reflection or Enum.valueOf on obfuscated names.
# - kotlinx.serialization models (CompletedQuiz, JSON sources) are kept by the rules below.
# Keep the R8 mapping file with every release: app/build/outputs/mapping/release/mapping.txt
# (it is also packed into the AAB and Play deobfuscates crash reports with it).

# Readable stack traces in crash reports (with the mapping file).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.geoquiz.app.**$$serializer { *; }
-keepclassmembers class com.geoquiz.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.geoquiz.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Google Play Games Services
-keep class com.google.android.gms.games.** { *; }
-dontwarn com.google.android.gms.games.**

# Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
