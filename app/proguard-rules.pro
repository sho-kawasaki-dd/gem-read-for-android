# Gemini SDK
-keep class com.google.ai.client.generativeai.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * { @androidx.room.Entity *; }
-keep class * { @androidx.room.Dao *; }

# Hilt
-keep class * { @dagger.hilt.android.lifecycle.HiltViewModel *; }

# Markdown renderer
-keep class com.mikepenz.markdown.** { *; }

# Cleartext traffic is denied by networkSecurityConfig, but we keep it here for safety
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable
