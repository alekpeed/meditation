# kotlinx.serialization: keep generated serializers for the core model persisted as JSON.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.meditation.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.meditation.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.meditation.core.**$$serializer { *; }

# Room entities/DAOs are referenced reflectively by generated code.
-keep class com.meditation.app.data.** { *; }
