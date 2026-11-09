-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.deepshawn.dictationhelper.**$$serializer { *; }
-keepclassmembers class com.deepshawn.dictationhelper.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
# pinyin4j
-keep class net.sourceforge.pinyin4j.** { *; }
-dontwarn net.sourceforge.pinyin4j.**
-dontwarn com.hp.hpl.sparta.**
-keep class com.hp.hpl.sparta.** { *; }
