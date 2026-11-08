---
title: Troubleshooting
description: Logs, common problems and how to report an issue.
---

## Read the logs first

The **Logs** tab shows live output from the app (**INTERNAL**) and Xray-core (**CORE**). Raise the **Xray log level** in Settings (advanced) for more detail. With advanced options on, you can also open the generated configuration in the viewer.

Search filters the current tab. Long-press a line to select it, then tap or drag to select more. Copy, save or share the selected lines, or, with nothing selected, save or share the whole tab. Exported files end in `-all.txt`, `-internal-only.txt`, `-core-only.txt` or `-selected.txt`, so you can tell what they contain. Clearing removes both app and Xray entries.

## Common problems

**The subscription won't refresh.**
Check whether the error mentions an insecure connection. By default Material Xray refuses plain HTTP, bad certificates and HTTPS-to-HTTP redirects. If you trust the source, turn on **Allow insecure updates** for that subscription.

**The provider says my device isn't recognized.**
The subscription may need a hardware ID or a specific User-Agent. Make sure **Send hardware ID** is on, or try the **Happ** User-Agent preset. See [request headers](/docs/providers/request-headers/).

**Routing rules were skipped.**
The provider's geodata couldn't be downloaded, so the app is in compatibility mode. Tap the banner on the Routing tab to retry.

**Connecting shows "VPN permission unavailable".**
Some TV firmwares (for example VIDAA) remove Android's VPN confirmation dialog, so the app can't ask for VPN access. If you can reach the device over ADB, grant it once with `adb shell appops set com.material.xray ACTIVATE_VPN allow`, then tap Connect again. The root service doesn't need this permission.

**Rootful mode falls back to rootless.**
Android's always-on VPN forces rootless mode. Turn it off for Material Xray if you want rootful.

## Reporting an issue

[Open an issue](https://github.com/AetherMagee/MaterialXray/issues) with:

- your Android version and device model,
- the service mode (rootless or rootful, and which root solution),
- steps to reproduce.

:::danger
Remove credentials, subscription URLs and other private information from any logs or configurations you share.
:::
