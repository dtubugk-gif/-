# Rikavon release rules. Hilt, Room, Compose and Lottie ship their own consumer rules.

# Keep kotlinx.serialization generated serializers for backup DTOs and mascot manifests.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class il.rikavon.**$$serializer { *; }
-keepclassmembers class il.rikavon.** { *** Companion; }
-keepclasseswithmembers class il.rikavon.** { kotlinx.serialization.KSerializer serializer(...); }

# Widget RemoteViews layout ids are referenced reflectively by the host.
-keep class il.rikavon.R$id { *; }
-keep class il.rikavon.R$layout { *; }
