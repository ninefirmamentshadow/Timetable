# Room generates code reflectively referenced at runtime; keep the schema.
-keep class com.sovereignops.timetable.data.** { *; }
# Data classes used as Room entities / DTOs.
-keepclassmembers class com.sovereignops.timetable.core.model.** { *; }
