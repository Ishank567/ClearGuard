# Google Play Store Deployment Guide

This guide provides step-by-step instructions on how to build, sign, and upload the **ShieldDNS** Android App Bundle (`.aab`) to the Google Play Store. It covers both manual generation/uploading and automated deployment using the GitHub Actions workflow.

---

## 1. Prerequisites & Play Console Setup

Before uploading, you must set up your app in the Google Play Console:

1. **Google Play Developer Account**: Ensure you have an active developer account on the [Google Play Console](https://play.google.com/console).
2. **Create Application**:
   - Click **Create app** on the dashboard.
   - Enter your App Name: `ShieldDNS` (or your preferred public name).
   - Select Default Language (e.g., English) and select **App** and **Free** (unless you plan to charge).
   - Confirm declarations and click **Create app**.
3. **App Package Name**:
   - The package name configured in `app/build.gradle` is `com.clearguard.app`. Ensure this package name is unique and matches what you register in the console.
4. **Store Presence Checklist**:
   - Set up your store listing: App Icon (512x512 PNG), Feature Graphic (1024x500 PNG), Screenshots (at least 2 for phone, 7" tablet, and 10" tablet).
   - Provide a link to your **Privacy Policy** (you can host the contents of [PRIVACY.md](file:///c:/Users/ishan/Music/adblocker/PRIVACY.md) on a static site or GitHub Pages).

---

## 2. Generating a Signing Keystore

Google Play requires all apps to be signed with a secure, private key. If you don't have a keystore yet, generate one.

### Option A: Generate using Android Studio
1. Open the project in Android Studio.
2. Navigate to **Build > Generate Signed Bundle / APK...**
3. Choose **Android App Bundle** and click **Next**.
4. Under **Key store path**, click **Create new...**
5. Specify a path (e.g., `c:\Users\ishan\Music\adblocker\app\keystore.jks`), passwords, alias (`shielddns-key`), and certificate details.
6. Click **OK** to generate and save your keystore.

### Option B: Generate using Command Line (`keytool`)
Run the following command in your terminal (make sure JDK is installed and in your PATH):
```powershell
keytool -genkey -v -keystore shielddns-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias shielddns-key
```
Keep this keystore file (`.jks`) safe! If you lose it, you won't be able to update your app on Google Play.

---

## 3. Method 1: Manual Build and Upload

### Step 1: Generate the Release App Bundle (`.aab`)
Google Play now requires the **Android App Bundle (.aab)** format for new applications because it allows Google Play to generate optimized APKs for each user's device configuration.

#### Using Android Studio:
1. Go to **Build > Generate Signed Bundle / APK...**
2. Select **Android App Bundle** and click **Next**.
3. Select your Keystore path, enter passwords and key alias.
4. Choose **release** build variant and click **Create**.
5. Once completed, the signed `.aab` file will be in `app/release/` or `app/build/outputs/bundle/release/`.

#### Using Command Line:
If you want to configure signing in your local build files:
Create or edit `app/build.gradle` to define the signing config:
```groovy
signingConfigs {
    release {
        storeFile file("path/to/your/keystore.jks")
        storePassword "keystore_password"
        keyAlias "key_alias"
        keyPassword "key_password"
    }
}
buildTypes {
    release {
        signingConfig signingConfigs.release
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```
*Note: Do not commit sensitive passwords to Git. Use environment variables or a local non-tracked property file (e.g., `local.properties`).*

Then run:
```powershell
gradle :app:bundleRelease
```

### Step 2: Upload to Google Play Console
1. Go to your app dashboard in the Google Play Console.
2. Navigate to **Testing > Internal testing** (recommended for initial tests) or **Production**.
3. Click **Create new release**.
4. Choose **Google Play App Signing** (let Google manage and protect your app signing key).
5. Drag and drop your signed `.aab` file into the **App bundles** box.
6. Complete the release notes and click **Save as draft**, then **Review release**, and finally **Start rollout**.

---

## 4. Method 2: Automated Deployment via GitHub Actions

We have configured a CI/CD workflow at [.github/workflows/google-play.yml](file:///c:/Users/ishan/Music/adblocker/.github/workflows/google-play.yml) to automatically compile, sign, and upload the App Bundle directly to Google Play when a GitHub release is published or triggered manually.

### Step 1: Link Google Play Console with Google Cloud
1. Go to the [Google Play Console](https://play.google.com/console).
2. Go to **Setup > API access**.
3. Create a Google Cloud project or link an existing one.
4. Click **Create Service Account** and follow the instructions to open the Google Cloud Console.
5. In Google Cloud, click **Create Service Account**, name it (e.g., `play-store-publisher`), and assign the role **Service Account User** (or no role is needed as permissions are mapped inside Play Console).
6. Create and download a **JSON key** for this service account. Keep this JSON file secure!

### Step 2: Set Service Account Permissions in Play Console
1. Return to the Google Play Console -> **API Access**.
2. Locate the new service account under **Service accounts** and click **Manage Play Console permissions** (or invite it as a user).
3. Under **App permissions**, select ShieldDNS and ensure it has:
   - *View app information (read-only)*
   - *Manage draft releases* (for draft uploads)
   - *Release to production, exclude devices, and use app signing* (for full publishing capability)
4. Click **Invite user** or **Save changes**.

### Step 3: Base64-Encode Your Keystore
GitHub Secrets can only store text, so you must encode your binary `.jks` keystore file as a Base64 string:

* **On Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("path\to\your\keystore.jks")) | Out-File -FilePath keystore_base64.txt
  ```
* **On macOS/Linux:**
  ```bash
  base64 -i path/to/your/keystore.jks | pbcopy
  ```

Copy the generated Base64 string.

### Step 4: Configure GitHub Secrets
In your GitHub repository, go to **Settings > Secrets and variables > Actions** and add the following repository secrets:

| Secret Name | Value Description | Example / Format |
|---|---|---|
| `PLAY_STORE_KEY_STORE_BASE64` | The Base64 string of your keystore file | Output from Step 3 |
| `PLAY_STORE_KEY_ALIAS` | The alias of your key | `shielddns-key` |
| `PLAY_STORE_KEY_STORE_PASSWORD` | The password of your keystore | `your_keystore_password` |
| `PLAY_STORE_KEY_PASSWORD` | The password of your key | `your_key_password` |
| `PLAY_STORE_SERVICE_ACCOUNT_JSON` | The entire contents of the Google Cloud Service Account JSON key file | `{"type": "service_account", ...}` |

### Step 5: Run the Workflow
* **Trigger via GitHub Release**: When you publish a new release in your GitHub repository, the workflow will trigger automatically and upload the bundle to Google Play.
* **Manual Trigger (Workflow Dispatch)**:
  1. Go to your GitHub repository -> **Actions** tab.
  2. Select the **Google Play Upload** workflow from the left sidebar.
  3. Click the **Run workflow** dropdown.
  4. Select the target branch (usually `main`), choose the target Google Play track (e.g., `internal` for testing, `alpha`, `beta`, or `production`), and the release status (e.g., `completed` or `draft`).
  5. Click **Run workflow**.

---

## 5. Summary of Built-in Automations

The repository has two active workflows:
1. **Android APK** ([.github/workflows/android-apk.yml](file:///c:/Users/ishan/Music/adblocker/.github/workflows/android-apk.yml)): Runs on pushes to `main`. Builds and publishes a downloadable debug APK to a rolling `latest` GitHub release. Excellent for manual sideload testing.
2. **Google Play Upload** ([.github/workflows/google-play.yml](file:///c:/Users/ishan/Music/adblocker/.github/workflows/google-play.yml)): Triggered manually or upon release publication. Builds, signs, and publishes the official Google Play App Bundle (`.aab`) to your chosen Google Play track.
