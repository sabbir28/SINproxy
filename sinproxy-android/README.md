# SINproxy Android & Desktop

Professional Java-based SNI Proxy and VPN Tool with MITM capabilities, SNI spoofing, and header injection.

## Features

- **Android VPN**: System-wide traffic interception using `VpnService`.
- **Desktop VPN (Emulator)**: Cross-platform support for Windows and Linux via automatic system proxy configuration.
- **SNI Spoofing**: Connect to target hosts using a custom SNI (Bug Host).
- **MITM Decryption**: Dynamic TLS certificate generation using Bouncy Castle for inspecting encrypted traffic.
- **Header Injection**: Automatic injection of `X-Online-Host` and `X-Forwarded-Host` headers.

## Project Structure

- `com.sinproxy.android.core`: Core proxy server and handler logic.
- `com.sinproxy.android.security`: Certificate management (Bouncy Castle).
- `com.sinproxy.android.vpn`: Android-specific VPN service.
- `com.sinproxy.android.desktop`: Desktop application for Windows/Linux.

## Getting Started

### Android
Check the [walkthrough.md](walkthrough.md) for detailed integration steps.

### Desktop (Windows/Linux)
1. Build the Shadow JAR: `./gradlew shadowJar`
2. Run the JAR: `java -jar build/libs/sinproxy-android-1.0.0-all.jar`
3. **Optional (Real VPN Mode)**: Place `tun2socks.exe` and `wintun.dll` in the same folder.
4. The app will automatically configure your system proxy or start the `tun2socks` VPN.

## Publishing
The project includes a GitHub Actions workflow to automatically build and publish the library when a new release is created.

## License
This project is licensed under the **SINproxy Commercial License**. Commercial use requires a $50 fee and a formal contract. Unauthorized commercial use is subject to a $1,000 fine. 

For inquiries, contact: **sabbirb228@gmail.com**
