---
title: Adding servers
description: Paste a link, scan a QR code, add manually, or open a deeplink.
---

Tap **Add new server or subscription** on the home screen. You can:

- **Paste from clipboard**: a subscription URL, or one or more share links separated by new lines, spaces, commas or semicolons.
- **Scan QR code**: the camera is used only for scanning, and you can always paste instead.
- **Add manually**: type the link and set options yourself. For a single share link, **Name** renames the server.

Material Xray also opens [`mxray://add/` deeplinks](/docs/providers/deeplinks/), so a button on your provider's site can add the subscription in one tap.

## What you can add

| Input | Result |
| --- | --- |
| An `https://` subscription URL | A subscription that refreshes on a schedule. |
| One or more `vless://`, `vmess://`, `trojan://`, `ss://`, `hysteria2://`, `wireguard://` or `socks://` links | Servers added to **Manual servers**. |

## Manual servers

Share links you add directly go into one **Manual servers** group. Nothing updates it, so edits to its servers save directly. Use **Delete** in a server's viewer to remove one server; removing the group from its menu deletes all of them.

Share links added in older versions, each shown as its own subscription, are moved into this group automatically, including when you restore an older backup.

Subscriptions can carry more: HTTP proxy lines and raw Xray JSON configurations. See [subscription format](/docs/providers/subscription-format/) and [share links](/docs/reference/share-links/).

## Subscription options

Hold a subscription's name and drag to reorder subscriptions.

Each subscription has its own settings, set when you add it manually or later by editing it. Expand **Advanced** for the fetch type, User-Agent, custom headers and insecure updates.

| Option | What it does |
| --- | --- |
| **Name** | Leave empty to use the provider's [`profile-title`](/docs/providers/response-headers/#profile-title). |
| **Auto update** | Every 1, 3, 6, 24 or 72 hours, or manual only. New subscriptions refresh every hour. |
| **JSON first** | Try `<url>/json` first and fall back to the saved URL. On by default. See [subscription format](/docs/providers/subscription-format/#json-first). |
| **Allow insecure updates** | Accept plain HTTP and untrusted or mismatched certificates. Off by default. |
| **User-Agent** | **Auto** identifies as Material Xray, **Happ** uses a Happ-compatible User-Agent, **Custom** lets you set the User-Agent and extra headers. See [request headers](/docs/providers/request-headers/). |

:::tip
To keep a server from being replaced on the next refresh, use **Lock configuration** in its viewer. **Unlock configuration** allows updates again.
:::

Server rows show a pencil for an edited configuration, a lock for a locked one, and a routing icon for edited routing rules.

## Hardware ID

Some providers require a device identifier (HWID). Material Xray sends one by default; turn it off under **Settings → Send hardware ID (HWID)**. HWID can help link traffic to your device. If a subscription [requires HWID](/docs/providers/response-headers/#subscription-always-hwid-enable) while sending is off, the app asks before adding it: **Proceed** sends HWID for that subscription only. While such a subscription is selected, the Settings switch stays on.

## TLS certificate fingerprints

For a VLESS, VMess or Trojan profile with TLS, open **View config → Edit → Security**. **Certificate fingerprint (SHA-256)** is separate from the **TLS ClientHello fingerprint** (such as Chrome).

Paste a fingerprint supplied by your administrator, or tap **Fetch certificate** to read the certificate from the server. The app connects to the draft's address and port with its SNI (or the address when SNI is empty) and ALPN, without sending proxy credentials or an HTTP request. The preview shows the endpoint, SHA-256, subject, issuer, validity dates and the result of ordinary TLS checks. **Use this fingerprint** fills it in; save the configuration to apply it. An existing, different fingerprint is never replaced automatically. Cancelling the request or changing the endpoint discards the result.

:::caution
Fetching a self-signed certificate does **not** prove the server's identity. Compare its fingerprint with the administrator through a separate trusted channel.
:::

Xray pins the SHA-256 of the whole certificate, not its public key, instead of the usual CA, hostname and expiry checks. A renewed or changed certificate blocks connections until you update the fingerprint. Leave the field empty for ordinary TLS verification. Several comma-separated fingerprints are accepted.

To keep your fingerprint through subscription updates, choose **Lock this server configuration** when saving. After an edit, the original share link is kept but marked as outdated. Share links carry it as `pcs` (or `pinnedPeerCertSha256`); raw Xray JSON profiles use `tlsSettings.pinnedPeerCertSha256`.
