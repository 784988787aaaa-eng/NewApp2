# SmartLedger Pro Android Proguard Rules

# Keep Room database and DAO interfaces
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Keep Model Entities
-keep class com.smartledger.pro.data.local.entity.** { *; }
-keep class com.smartledger.pro.data.local.dao.** { *; }

# Keep Kotlin Coroutines & Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Compose Runtime
-keep class androidx.compose.runtime.** { *; }
