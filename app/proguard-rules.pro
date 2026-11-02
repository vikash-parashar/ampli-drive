# MapLibre ships its own consumer rules inside the AAR.
# Keep the notification listener (referenced only from the manifest).
-keep class com.vikash.ampli.drive.service.DriveNotificationListener { *; }

# Vosk talks to its native library through JNA, which finds classes and fields by name.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**

# Ampli voice engine (libamplitts): JNI binds FliteNative's native methods by class and method name.
-keep class com.vikash.ampli.drive.voice.tts.FliteNative { native <methods>; }
