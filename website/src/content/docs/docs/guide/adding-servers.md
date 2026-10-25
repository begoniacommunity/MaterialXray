---
title: Adding servers
description: Paste a link, scan a QR code, add manually, or open a deeplink.
---

Tap **Add new server or subscription** on the home screen. You can:

- **Paste from clipboard**: a subscription URL or a single share link.
- **Scan QR code**: the camera is used only for scanning, and you can always paste instead.
- **Add manually**: type the link and set options yourself.

Material Xray also opens [`mxray://add/` deeplinks](/docs/providers/deeplinks/), so a button on your provider's site can add the subscription in one tap.

## What you can add

| Input | Result |
| --- | --- |
| An `https://` subscription URL | A subscription that refreshes on a schedule. |
| A single `vless://`, `vmess://`, `trojan://`, `ss://` or `hysteria2://` link | One server. |

Subscriptions can carry more: HTTP, SOCKS and WireGuard lines, plus raw Xray JSON configurations. See [subscription format](/docs/providers/subscription-format/) and [share links](/docs/reference/share-links/).

## Subscription options

Subscription menus on Home use the horizontal three-dot icon, matching Routing.

Hold the name in a subscription header and drag to reorder subscriptions, whether collapsed or expanded. Other cards slide into their new positions with a short, smooth transition. Haptic feedback marks the start of dragging and each change of position. This movement applies only when their order changes; expanding and collapsing a card keeps the usual reveal animation. While dragging, header controls fade into a centered drag handle. The subscription name and used traffic counter remain in place without fading or being replaced. The list scrolls near its edges, and the new order is saved when you release. Screen readers offer **Move up** and **Move down** actions on each header.

Each subscription has its own settings, set when you add it manually or later by editing the subscription. Expand **Advanced** to show the fetch type, User-Agent, custom headers and insecure-update options; the dialog resizes smoothly and keeps its fields scrollable. Both dialogs move above the keyboard when it opens and return to the center when it closes, including with **Advanced** collapsed. Custom User-Agent and header fields use the same smooth expansion and collapse.

| Option | What it does |
| --- | --- |
| **Name** | Leave empty to use the provider's [`profile-title`](/docs/providers/response-headers/#profile-title). |
| **Auto update** | Every 1, 3, 6, 24 or 72 hours, or manual only. New subscriptions refresh every hour. |
| **JSON first** | Try `<url>/json` first and fall back to the saved URL. On by default. See [subscription format](/docs/providers/subscription-format/#json-first). |
| **Allow insecure updates** | Accept plain HTTP and untrusted or mismatched certificates. Off by default. |
| **User-Agent** | **Auto** identifies as Material Xray, **Happ** uses a Happ-compatible User-Agent, **Custom** lets you set the User-Agent and extra headers. See [request headers](/docs/providers/request-headers/). |

:::tip
To keep a server from being replaced on the next refresh, use **Lock configuration** in its viewer. A closed lock and a red **Server configuration is locked** banner show that subscription updates cannot replace it. Use **Unlock configuration** to allow updates again.
:::

Server rows show a filled pencil for an edited configuration, a filled lock for a locked configuration, and a routing icon for edited server or subscription rules. These indicators have the same size and spacing, in that order. Resetting routing clears its icon while retaining the configuration edit.

## Hardware ID

Some providers require a device identifier (HWID). Material Xray sends one by default. You can turn it off under **Settings → Send hardware ID (HWID)**. If a subscription [requires HWID](/docs/providers/response-headers/#subscription-always-hwid-enable) while sending is off, the app asks before adding it. **Cancel** leaves it unadded. **Proceed** permits HWID for that subscription and fetches it again with HWID; your global choice stays unchanged. The warning explains that HWID can help link traffic to your device and reduce anonymity. Selecting a subscription that requires HWID forces the Settings switch on and locks it. Switching to another subscription restores your saved choice.

For an automatic multi-server profile, Home shows only the number of servers it connects.

## TLS certificate fingerprints

For a VLESS, VMess or Trojan profile with TLS, open **View config → Edit → Security**. **Certificate fingerprint (SHA-256)** is separate from the **TLS ClientHello fingerprint** (such as Chrome).

Paste a fingerprint supplied by your administrator, or tap **Fetch certificate**. The app contacts the address and port in the current draft, sends the configured SNI (or uses the address when SNI is empty), negotiates the ALPN protocols from the draft, and retrieves the certificate without sending proxy credentials or an HTTP request. The preview shows the endpoint, SHA-256, subject, issuer, validity dates and the result of ordinary TLS trust and name checks.

Fetching a self-signed certificate does **not** prove the server's identity. Compare its fingerprint with the administrator through a separate trusted channel. **Use this fingerprint** fills the draft only; save the configuration to apply it. A differing existing fingerprint is never replaced automatically. Cancelling the request or changing the endpoint discards the result.

Xray pins SHA-256 of the complete DER certificate, not its public key. Accepting a server certificate replaces ordinary CA, hostname and expiry verification with an exact certificate match. A renewed or changed certificate therefore blocks connections until you explicitly update the fingerprint. Leave the field empty for ordinary TLS verification. Colon-separated hexadecimal and multiple comma-separated fingerprints are accepted.

When saving a subscription profile, choose **Lock this server configuration** if future subscription updates must preserve your fingerprint. The original share link remains unchanged and is marked as outdated after an edit; backups preserve the edited configuration and fingerprint. VLESS/Trojan links accept `pcs` (or `pinnedPeerCertSha256`); VMess JSON links accept the same fields. Raw Xray JSON profiles keep using the JSON editor and `tlsSettings.pinnedPeerCertSha256`.
