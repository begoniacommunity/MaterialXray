---
title: Troubleshooting
description: Logs, common problems and how to report an issue.
---

## Read the logs first

The **Logs** tab shows live output from both the app and Xray-core. Raise the **Xray log level** in Settings (advanced) for more detail. With advanced options on, you can also open the generated configuration in the viewer.

Internal messages use **INTERNAL**, and core messages use **CORE**, on screen and in exported text. The All, Internal and Core tabs use the Material Symbols Notes, Android and Manufacturing icons.

The All/Internal/Core selector floats over the bottom gradient. The selected icon grows into view as the page changes. Tapping a distant tab animates only the outgoing and destination tabs, while swipes and their settling flings follow the pager position. End padding ends exactly at the upper edge of the fade. The scroll-to-bottom button is drawn above the fade and sits 8 dp above the selector, matching the gap between the selector and the app navigation bar. The upper faded area is 16 dp high, with no additional scroll reserve. Search and the selector crossfade and move smoothly together with their gradient and reserved space. Selecting log lines slides and fades the selector and gradient away and releases their reserved space. The scroll-to-bottom button also hides during selection. Clearing selection restores them.

Tap **Search logs** (the Find in Page icon just before the export menu) to open a search field above the keyboard. Search filters the current **All**, **Internal** or **Core** tab by message text, ignoring case. The header's close button closes search and the keyboard. With a query entered, the header shows the number of matching lines; export and clear actions are hidden when no lines match. Dismissing the keyboard keeps search open while a query or selection is present. Opening the export menu by touch keeps the search keyboard in place and preserves selected lines even when the search field is empty. Back closes the menu before search or selection. With an empty search and no selection, the close button is the only header content. Swiping between log tabs is disabled while search is open or log lines are selected.

While log lines are selected, the top bar uses the scrolled background color and the fade below it fades out. Clearing selection restores the normal bar and fade.

Long-press a log line to select it, then drag or tap to select more. When at least one line is selected and the current list contains more than one line, **Select all** appears as the first toolbar action and selects every visible result in the active tab. Once all those lines are selected, it becomes **Deselect**, which clears the selection while keeping any search open. The selection toolbar can copy the selected lines; its export menu offers **Save to file** and **Share logs** for just those lines. Without a selection, the export menu saves or shares every log in the active **All**, **Internal** or **Core** tab. Filenames end in `-all.txt`, `-internal-only.txt`, `-core-only.txt` or `-selected.txt` to identify the exported scope. Clearing logs asks for confirmation and removes both app and Xray entries.

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
