# Protobuf-lite: only keep field names, not entire classes.
# GeneratedMessageLite.dynamicMethod accesses fields by name,
# so renaming them (qn_ -> a) breaks deserialization.
# Let R8 still remove unused proto classes via normal reachability.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Protobuf runtime (generated code calls protected methods like parseFrom/dynamicMethod)
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**