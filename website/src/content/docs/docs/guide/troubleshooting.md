---
title: Troubleshooting
description: Logs, common problems and how to report an issue.
---

## Read the logs first

The **Logs** tab shows live output from both the app and Xray-core. Raise the **Xray log level** in Settings (advanced) for more detail. With advanced options on, you can also open the generated configuration in the viewer.

Tap **Search logs** to open a search field above the keyboard. Search filters the current **All**, **App** or **Xray** tab by message text, ignoring case. The header's close button closes search and the keyboard. With a query entered, the header shows the number of matching lines; export and clear actions are hidden when no lines match. Dismissing the keyboard with a back gesture keeps a nonempty query and its results visible. With an empty search and no selection, the close button is the only header content.

Long-press a log line to select it, then drag or tap to select more. When at least one line is selected and the current list contains more than one line, **Select all** appears as the first toolbar action and selects every visible result in the active tab. Once all those lines are selected, it becomes **Deselect**, which clears the selection while keeping any search open. The selection toolbar can copy the selected lines; its export menu offers **Save to file** and **Share logs** for just those lines. Without a selection, the export menu saves or shares every log in the active **All**, **App** or **Xray** tab. Filenames end in `-all.txt`, `-app-only.txt`, `-core-only.txt` or `-selected.txt` to identify the exported scope. Clearing logs asks for confirmation and removes both app and Xray entries.

The JSON editor for the active Xray configuration or an individual server has no fade or fixed bands at its top and bottom edges. Its content padding scrolls with the text, and a small gap keeps the text clear of the outline.

## Common problems

**The subscription won't refresh.**
Check whether the error mentions an insecure connection. By default Material Xray refuses plain HTTP, bad certificates and HTTPS-to-HTTP redirects. If you trust the source, turn on **Allow insecure updates** for that subscription.

**The provider says my device isn't recognized.**
The subscription may need a hardware ID or a specific User-Agent. Make sure **Send hardware ID** is on, or try the **Happ** User-Agent preset. See [request headers](/docs/providers/request-headers/).

**Routing rules were skipped.**
The provider's geodata couldn't be downloaded, so the app is in compatibility mode. Tap the banner on the Routing tab to retry.

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
