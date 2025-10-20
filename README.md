# Cicero.ai Android Project

This project requires access to the Android SDK in order to build and run. If the SDK
location cannot be resolved automatically, Gradle will abort with an error similar to:

```
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file.
```

To configure the SDK path:

1. Install the Android SDK on your machine (for example, via Android Studio or the command line tools).
2. Copy the `local.properties.example` file to `local.properties` in the project root.
3. Edit the new `local.properties` file so that `sdk.dir` points to the absolute path of your Android SDK installation.

```properties
sdk.dir=/absolute/path/to/your/Android/Sdk
```

Alternatively, you can export the `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) environment variable before running Gradle:

```bash
export ANDROID_HOME=/absolute/path/to/your/Android/Sdk
./gradlew assemble
```

> **Note:** The `local.properties` file is ignored by Git because it typically contains
> developer-specific paths. Do not commit your customized `local.properties` file.
