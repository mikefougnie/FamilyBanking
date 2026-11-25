# kotlinx.serialization: keep generated serializers for the sync messages.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.godskingdomnow.familybank.sync.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.godskingdomnow.familybank.sync.**$$serializer { *; }
