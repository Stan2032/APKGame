# kotlinx-serialization: keep generated serializers for our @Serializable
# models (Profile, SystemSpec, etc. live in :core under this package root).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class io.github.stan2032.gravitygambit.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.stan2032.gravitygambit.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.stan2032.gravitygambit.**$$serializer { *; }

# Play Billing ships consumer rules; nothing extra needed. Compose is fully
# R8-aware. No reflection anywhere else in this codebase by design.
