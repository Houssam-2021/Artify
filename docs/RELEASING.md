# Releasing Artify

Signing material **never** goes in the repository. Builds without signing
secrets produce an unsigned release.

## 1. Create the upload key (once, on your own machine)

Store it **outside** the project folder, for example in your home directory:

```bash
keytool -genkeypair -v -keystore ~/keys/artify-upload.jks -alias artify -keyalg RSA -keysize 4096 -validity 10000
```

Back it up somewhere private (password manager / encrypted drive). Enrol the
app in **Play App Signing** so Google keeps the real app-signing key and this is
only an upload key that can be reset if lost or leaked.

## 2. Local signed builds

Put these in `~/.gradle/gradle.properties` (your user folder, not the project):

```properties
ARTIFY_KEYSTORE_FILE=/home/you/keys/artify-upload.jks
ARTIFY_KEYSTORE_PASSWORD=...
ARTIFY_KEY_ALIAS=artify
ARTIFY_KEY_PASSWORD=...
```

Then run `./gradlew :app:bundleRelease`. The build refuses to use a keystore
located inside the repository.

## 3. CI signed builds

In the GitHub repository go to **Settings → Environments → New environment**,
name it `release`, and add required reviewers. Then add these environment secrets:

| Secret | Value |
|---|---|
| `ARTIFY_KEYSTORE_BASE64` | output of `base64 -w0 artify-upload.jks` |
| `ARTIFY_KEYSTORE_PASSWORD` | keystore password |
| `ARTIFY_KEY_ALIAS` | `artify` |
| `ARTIFY_KEY_PASSWORD` | key password |

Push a tag such as `v1.0.0`. The `Release` workflow builds, signs and uploads
the AAB/APK as workflow artifacts.
