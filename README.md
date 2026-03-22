# SINproxy

A professional SNI Proxy and Bug Tester.

## Built & Released Artifacts

This project is configured with a comprehensive CI/CD pipeline that automatically produces the following artifacts on every release:

- **Python Package**: `.whl` (Wheel) file for easy installation via pip.
- **Android Library**: `.aar` file, ready to be integrated into Android projects.
- **Windows Desktop**: `.exe` standalone executable (no JVM installation required).
- **Linux/Desktop**: `.jar` fat-executable for cross-platform Java use.

## How to Release

Release automation is triggered via GitHub Actions. To publish a new version:

1.  **Tag the commit** with a version number (e.g., `v1.0.1`):
    ```bash
    git tag v1.0.1
    git push origin v1.0.1
    ```
2.  **GitHub Actions** will automatically:
    - Build all platform artifacts.
    - Create a new GitHub Release.
    - Upload all files to the release page.

## Local Development

### Python
```bash
pip install -e .
```

### Java/Android
```bash
cd sinproxy-android
./gradlew build
```
