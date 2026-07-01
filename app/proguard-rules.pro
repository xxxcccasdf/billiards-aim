# ProGuard rules for Billiards Aim
# Keep WebView classes
-keep class android.webkit.** { *; }
-keep class androidx.webkit.** { *; }
# Keep service classes
-keep class com.billiards.aim.** { *; }
# Keep WebViewAssetLoader
-keepclassmembers class androidx.webkit.WebViewAssetLoader$* { *; }
# Remove logging
-assumenosideeffects class android.util.Log { *; }
