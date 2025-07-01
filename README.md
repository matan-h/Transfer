# Transfer

A simple local file server app for Android. Download and upload files quickly across devices over Wi-Fi — no cables, no cloud.

<table>
  <tr>
    <td>
    	<img src="screenshots/result/01.png" width="150px" />
    	<img src="screenshots/result/02.png" width="150px" />
    	<img src="screenshots/result/03.png" width="150px" />
    	<img src="screenshots/result/04.png" width="150px" />
    	<img src="screenshots/result/05.png" width="150px" />
    	<img src="screenshots/result/06.png" width="150px" />
    </td>
  </tr>
</table>

Transfer makes sharing files across your local network incredibly straightforward. Think of it as a temporary USB stick you can access from any computer or device with a web browser — but without the physical stick, powered by a clean and simple UI.

Basically, a better alternative to running `uploadserver` through Termux.

## The Problem It Solves

Imagine you need to transfer a file between your laptop and desktop — but you just want to send the file and move on:

* You don’t have a USB stick or cable handy
* You don’t want to configure SMB (enable/run Samba, then download a client)
* You don’t want to use the cloud — either because it’s slow, unprivate, or you simply don’t have easy access to it

## Key Features

* **Effortless LAN Sharing**: Once Transfer is active, it serves files from your chosen shared folder over HTTP. Any device on the same Wi-Fi can connect using a simple web address shown in the app.
* **Configurable Security**:
    * **IP Permissions**: By default, new devices attempting to connect trigger an "Allow/Deny" popup on your phone, giving you control over who accesses your files. This can be turned off for trusted networks.
    * **Password Protection**: For an added layer, you can secure access with a password (off by default).
* **Powerful CLI Access (curl-friendly)**:
    * Transfer works great with command-line tools. Upload files directly using `curl -T yourfile.txt <your-phone-ip>:8000`.Also,you can download files using`curl <phone-ip>:8000/yourfile.txt`.
* **Dual Browse UI**: Manage and access your shared files directly within the Transfer app on your Android device, or through the intuitive web interface on any connected computer.
* **Quick In-App Transfers**:
    * **Upload**: Easily select files from your phone’s storage to add them to the shared folder.
    * **Paste**: Paste text from your phone’s clipboard directly into a new `.txt` file in the shared folder with a single tap.

## How to get the app
Recommended way:
from [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/com.matanh.transfer) (use an F-Droid client like Neo Store, Droid-ify, or add IzzyOnDroid repo.)

  <a href="https://apt.izzysoft.de/fdroid/index/apk/com.matanh.transfer">
    <img alt="Get it on IzzyOnDroid" src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="54">
  </a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.matanh.transfer%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fmatan-h%2FTransfer%22%2C%22author%22%3A%22matan-h%22%2C%22name%22%3A%22Transfer%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Afalse%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Atrue%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Atrue%2C%5C%22releaseTitleAsVersion%5C%22%3Atrue%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22Transfer%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22matan%20h%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22A%20simple%20local%20file%20server%20app%20for%20Android.%20Download%20and%20upload%20files%20quickly%20across%20devices%20over%20Wi-Fi%20%E2%80%94%20no%20cables%2C%20no%20cloud.%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%7D%22%2C%22overrideSource%22%3Anull%7D">
    <img height="54" src="https://github.com/ImranR98/Obtainium/blob/main/assets/graphics/badge_obtainium.png?raw=true" alt="Get it on Obtainium"/>
  </a>

Directly from [github releases](https://github.com/matan-h/Transfer/releases) (You can also point Obtainium to this link.)


## Getting Started

1. Install and open Transfer on your Android device.
2. Grant necessary permissions and select a folder you wish to share (suggestion: create a new folder called `Storage` in your home directory).
3. Tap "Start Server."
4. The app will display an IP address (e.g., `http://192.168.1.X:8000`).
5. Open this address in a web browser on any other device connected to the same Wi-Fi network.
6. You're in! If IP permissions are on (default), you'll get a prompt on your phone to allow the new device.

It's designed to be that simple. Enjoy your new wireless drive.

## FAQ
### Can I set a specific IP address to use every time?
Yes, you can set a [static IP](https://junipersys.com/support/article/14695) on your Android device.

[asked here](https://www.reddit.com/r/fossdroid/comments/1l3yi6m/comment/mw9t8lt)

### Can I use Transfer without an internet connection?
Yes, Transfer works over your local Wi-Fi network, so an internet connection is not required. However, all devices must be connected to the same local network.

### What happens to files I share/upload to the Transfer
Transfer copy the files to the shared folder you set earlier.

## Planned changes

- [ ] add an option to change the port in the settings
- [ ] fallback to hotspot IP in the display.
- [ ] automatically update the IP when Wifi changes

## Contributing

Any contributions, bugs, or feedback are welcome!
Feel free to open an issue or PR.

## License
this repo is Licensed under the MIT license.
