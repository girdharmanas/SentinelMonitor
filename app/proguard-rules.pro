# Sentinel Monitor ProGuard & R8 Optimization Rules

# Keep telemetry & preference models used in serialization
-keep class com.sentinel.monitor.data.model.** { *; }
-keep class com.sentinel.monitor.data.preferences.** { *; }
-keep class com.sentinel.monitor.service.** { *; }
-keep class com.sentinel.monitor.widget.** { *; }
