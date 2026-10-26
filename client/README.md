# Star Wars: Commander - Android Client Setup

The game client is the original Android APK (v7.8.1, build 253), patched to redirect API calls and CDN asset downloads to the private server instead of Zynga/Disney's decommissioned endpoints.

---

### Keystore

All development APKs are signed with the shared key:
* **Keystore file:** `client/swc-dev.keystore`
* **Keystore password:** `swcserver`
* **Key alias:** `swc`
* **Key password:** `swcserver`

> [!IMPORTANT]
> Always sign with this keystore so newly patched builds can be installed over previous builds (`adb install -r`) without requiring an uninstall, preserving your local game progress on the device.

---

### Patching the APK

Because the server address is baked into IL2CPP metadata (`assets/bin/Data/Managed/Metadata/global-metadata.dat`), the APK needs to target your server's IP/domain and port.

#### Prerequisites
* `python3`
* `zip` and `unzip`
* Android build tools (`zipalign` and `apksigner`)
* The base APK (e.g. `star-wars-commander-7-8-1-253.apk`)

#### Automated Build

Run the build script with your server address (e.g. `192.168.1.100:8080`):

```bash
./tools/build_client.sh <SERVER_IP>:8080 /path/to/star-wars-commander-7-8-1-253.apk
```

The script will:
1. Extract `global-metadata.dat`.
2. Rewrite API/CDN domains and point dead analytics (Swrve, Bugsnag) to closed local ports.
3. Update and align the APK.
4. Sign it using `client/swc-dev.keystore`.
5. Output `client/swc-patched-<IP>.apk` (this file is ignored by Git).

#### Installing on Device

```bash
adb install -r client/swc-patched-<IP>.apk
```

Ensure your phone and server are on the same Wi-Fi network (or the server is accessible via public IP/domain).
