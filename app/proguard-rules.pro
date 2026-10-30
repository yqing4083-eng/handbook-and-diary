# Preserve the personal APK's existing JNI names, persisted model API and the
# same production components exercised by platform instrumentation. Optimize
# method bodies while keeping those entry points available in the release APK.
-keep,allowoptimization class com.qingyu.personalaios.** { *; }
-dontobfuscate
