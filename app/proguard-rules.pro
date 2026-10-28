# ProGuard rules for Input Monitor

# Keep Room entities
-keep class com.example.inputmonitor.data.entity.** { *; }

# Keep Room DAOs
-keep interface com.example.inputmonitor.data.dao.** { *; }

# SQLCipher
-keep class net.zetetic.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
