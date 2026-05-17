# Gradle Wrapper

The binary `gradle-wrapper.jar` is not included.

To regenerate the wrapper:

1. Open this project in Android Studio Hedgehog (2023.1.1) or newer — Studio
   provisions the wrapper automatically on first sync.

2. Or, install Gradle 8.7 locally and run from the project root:

   ```
   gradle wrapper --gradle-version 8.7 --distribution-type bin
   ```

This will generate `gradle-wrapper.jar`, `gradlew`, and `gradlew.bat` next to
this README.
