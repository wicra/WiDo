# Règles R8 de WiDo.

# kotlinx.serialization : configuration, sites perso, réglages appris (module core).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class dev.wido.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.wido.**$$serializer { *; }
-keepclasseswithmembers class dev.wido.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Traces de plantage lisibles.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
