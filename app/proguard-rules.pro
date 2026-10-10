# Add project specific ProGuard rules here.
# https://developer.android.com/guide/developing/tools/proguard.html

# Keep TFLite model classes from being stripped
-keep class org.tensorflow.** { *; }
-keep class com.google.flatbuffers.** { *; }

# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.zemmy.sigurvita.**$$serializer { *; }
-keepclassmembers class com.zemmy.sigurvita.** { *** Companion; }
-keepclasseswithmembers class com.zemmy.sigurvita.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
