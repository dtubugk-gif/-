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

# The Claude SDK (optional AI brain) maps JSON through Jackson reflection; keep it whole.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.anthropic.**
-dontwarn com.fasterxml.jackson.**
-dontwarn okhttp3.**
-dontwarn okio.**
# Jackson (pulled in by the Claude SDK) references reflection types Android does not ship; never called here.
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
