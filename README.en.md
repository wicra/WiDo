# WiDo

A remote-friendly **streaming browser for Google TV / Android TV** with ad blocking configured **remotely**. Open source (MIT).

WiDo shows a menu of your sites, opens them full screen in a WebView with an on-screen cursor driven by the remote's arrow keys, blocks ad domains and unwanted redirects, and reads its site list and rules from a JSON file on GitHub, so changing a site's domain is a one-line edit. Updates come from GitHub releases (SHA-256 verified, Android asks for confirmation). Home Assistant can launch a site with `adb shell am start -n dev.wido.tv/.PlayerActivity --es site <id>`.

**Status: MVP (v0.1).** The `core` module is unit-tested; the Android app has not been run on a real device yet. WiDo ships no content and no site list, and does not circumvent DRM.

Docs are in French, in [`docs/`](docs/README.md). Start with [Installation](docs/INSTALL.md) and [Configuration](docs/CONFIGURATION.md). Contributions in English or French are welcome.
