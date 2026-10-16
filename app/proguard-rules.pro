# Read by the minified release build (D16). Course Invoicer uses no reflection, serialization or DI
# framework, so the default proguard-android-optimize.txt rules and the libraries' own consumer
# rules are enough. If you add reflection or JNI, add keep rules here; CI's emulator smoke test
# launches and exercises the minified APK.
