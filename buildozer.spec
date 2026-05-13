[app]
title = System Data Sync
package.name = utility_data_sync
package.domain = com.utility.data
source.dir = .
source.include_exts = py,png,jpg,kv,atlas
version = 69.0
requirements = python3,kivy,plyer,android,requests,certifi
orientation = portrait
osx.python_version = 3
osx.kivy_version = 1.9.1
fullscreen = 0
android.permissions = INTERNET, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC
android.api = 34
android.minapi = 21
android.sdk = 34
android.ndk = 25b
android.archs = arm64-v8a, armeabi-v7a
android.allow_backup = True


