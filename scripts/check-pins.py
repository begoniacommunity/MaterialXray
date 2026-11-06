#!/usr/bin/env python3
"""Checks every pinned version against the latest upstream one and bumps the outdated ones.

  scripts/check-pins.py                check every pin, then offer to bump the outdated ones
  scripts/check-pins.py core kotlin    only these: groups (libraries, build, core, actions) or pin names
  scripts/check-pins.py --check        report only
  scripts/check-pins.py --yes          bump without asking
  scripts/check-pins.py --regenerate   only regenerate gradle/verification-metadata.xml

After bumping a Gradle dependency it regenerates gradle/verification-metadata.xml and adds the
per-OS artifacts a Linux build doesn't download. Review the diff and run the QA from AGENTS.md.
"""

import argparse
import functools
import hashlib
import json
import re
import subprocess
import sys
import tomllib
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GOOGLE = "https://dl.google.com/android/maven2"
CENTRAL = "https://repo1.maven.org/maven2"
PORTAL = "https://plugins.gradle.org/m2"
CATALOG = ROOT / "gradle/libs.versions.toml"
METADATA = ROOT / "gradle/verification-metadata.xml"
WRAPPER = ROOT / "gradle/wrapper/gradle-wrapper.properties"
CONVENTIONS = ROOT / "build-logic/src/main/kotlin/MaterialXrayConventions.kt"
CI = ROOT / ".github/workflows/ci.yml"
RELEASE = ROOT / ".github/workflows/release.yml"
WORKFLOWS = sorted((ROOT / ".github/workflows").glob("*.yml"))
VERIFY_PAGE = ROOT / "website/src/content/docs/docs/reference/verifying-releases.md"

# Moves only together with another pin, so it is reported but never bumped here.
HELD = {"sqlite": "follows Room's androidx.sqlite line"}
# The Gradle tasks whose dependencies gradle/verification-metadata.xml has to cover. --continue keeps
# a failing test or lint check from cutting the recording short.
REGENERATE = [
    "./gradlew", "--continue", "--write-verification-metadata", "sha256", "test", ":app:assembleDebug",
    ":app:assembleRelease", "-x", "validateReleaseTelemetry", "detektMain", "detektTest",
    "-x", "detektRelease", ":app:lintDebug", "ktlintCheck", "detekt",
    ":core:android:assembleDebugAndroidTest",
]
PROTOC_PLATFORMS = ["linux-aarch_64", "osx-x86_64", "osx-aarch_64", "windows-x86_64"]
# Artifacts published per OS (and CPU): a Linux regeneration only records the Linux one.
FOREIGN = [
    ("com.android.tools.build", "aapt2", GOOGLE, ["osx", "windows"], "jar"),
    ("com.google.protobuf", "protoc", CENTRAL, PROTOC_PLATFORMS, "exe"),
    ("io.grpc", "protoc-gen-grpc-java", CENTRAL, PROTOC_PLATFORMS, "exe"),
]
CLEAR_LINE = "\r\033[K"
PRERELEASE = re.compile(r"alpha|beta|rc|dev|eap|snapshot|preview|-m\d", re.IGNORECASE)


class Pin:
    """A pinned version. Listing it reads only the repository; [find_latest] asks upstream."""

    def __init__(self, name, current, find_latest, bump, regenerate=False):
        self.name, self.current, self.find_latest, self.bump = name, current, find_latest, bump
        self.regenerate, self.held = regenerate, HELD.get(name)
        self.latest = self.error = None

    @property
    def outdated(self):
        return self.latest is not None and self.latest != self.current and not self.held


# Upstream lookups


def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": "materialxray-check-pins"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.geturl(), response.read()


def latest_github_release(repository):
    # GitHub's "latest" release skips drafts and prereleases.
    return get(f"https://github.com/{repository}/releases/latest")[0].rsplit("/", 1)[1]


def github_tags(repository):
    """Maps each tag to the commit it points at, peeling annotated tags."""
    lines = subprocess.run(
        ["git", "ls-remote", "--tags", f"https://github.com/{repository}"], check=True, capture_output=True, text=True,
    ).stdout.splitlines()
    tags = {}
    for sha, ref in (line.split() for line in lines):
        tag = ref.removeprefix("refs/tags/")
        if tag.endswith("^{}"):
            tags[tag.removesuffix("^{}")] = sha
        else:
            tags.setdefault(tag, sha)
    return tags


def version_key(version):
    # Numbers compare numerically, and a release sorts above its own alphas, betas and RCs.
    parts = [(1, int(t)) if t.isdigit() else (-1, t.lower()) for t in re.findall(r"\d+|[A-Za-z]+", version)]
    return parts + [(0,)]


def newest(versions, current, major=None):
    allow_prerelease = bool(PRERELEASE.search(current))
    candidates = [
        v for v in versions
        if (allow_prerelease or not PRERELEASE.search(v)) and (major is None or v.split(".")[0] == major)
    ]
    return max(candidates + [current], key=version_key)


def maven_versions(group, name, repositories):
    path = f"{group.replace('.', '/')}/{name}/maven-metadata.xml"
    for repository in repositories:
        try:
            return [v.text for v in ET.fromstring(get(f"{repository}/{path}")[1]).iter("version")]
        except urllib.error.HTTPError:
            continue
    raise LookupError(f"{group}:{name} not found")


def library_versions(group, name):
    google_first = group.startswith(("androidx.", "com.android.", "com.google.android."))
    return maven_versions(group, name, ([GOOGLE, CENTRAL] if google_first else [CENTRAL, GOOGLE]) + [PORTAL])


def plugin_versions(plugin_id):
    return maven_versions(plugin_id, f"{plugin_id}.gradle.plugin", [PORTAL, GOOGLE, CENTRAL])


@functools.cache
def stable_sdk_packages():
    stable = {}
    for package in ET.fromstring(get("https://dl.google.com/android/repository/repository2-3.xml")[1]).iter("remotePackage"):
        channel = package.find("channelRef")
        if channel is not None and channel.get("ref") == "channel-0":
            kind, _, version = package.get("path").partition(";")
            stable.setdefault(kind, []).append(version)
    return stable


# Repository edits


def replace(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise RuntimeError(f"expected exactly one {old!r} in {path.relative_to(ROOT)}")
    path.write_text(text.replace(old, new))


def read(path, pattern):
    match = re.search(pattern, path.read_text(), re.MULTILINE)
    if not match:
        raise RuntimeError(f"{pattern!r} not found in {path.relative_to(ROOT)}")
    return match.group(1)


def run(*command):
    subprocess.run(command, cwd=ROOT, check=True)


# Groups


def library_pins():
    catalog = tomllib.loads(CATALOG.read_text())
    sources = {}
    for library in catalog["libraries"].values():
        if isinstance(library.get("version"), dict):
            sources.setdefault(library["version"]["ref"], functools.partial(library_versions, library["group"], library["name"]))
    for plugin in catalog["plugins"].values():
        sources.setdefault(plugin["version"]["ref"], functools.partial(plugin_versions, plugin["id"]))

    def bump_key(key, current):
        def bump(latest):
            text, count = re.subn(
                rf'^{re.escape(key)} = "{re.escape(current)}"$', f'{key} = "{latest}"', CATALOG.read_text(), flags=re.M,
            )
            if count != 1:
                raise RuntimeError(f"{key} not found in libs.versions.toml")
            CATALOG.write_text(text)
        return bump

    def bump_inline(alias, current):
        def bump(latest):
            text = CATALOG.read_text()
            line = re.search(rf"^{re.escape(alias)} = \{{.*\}}$", text, re.M).group(0)
            CATALOG.write_text(text.replace(line, line.replace(f'version = "{current}"', f'version = "{latest}"')))
        return bump

    pins = [
        Pin(key, current, lambda s=sources[key], c=current: newest(s(), c), bump_key(key, current), regenerate=True)
        for key, current in catalog["versions"].items()
    ]
    for alias, library in catalog["libraries"].items():
        if isinstance(library.get("version"), str):
            current = library["version"]
            find = lambda g=library["group"], n=library["name"], c=current: newest(library_versions(g, n), c)
            pins.append(Pin(alias, current, find, bump_inline(alias, current), regenerate=True))
    return pins


def build_pins():
    pins = []

    current_gradle = read(WRAPPER, r"gradle-([^-]+)-bin\.zip")

    def bump_gradle(version):
        sha256 = get(f"https://services.gradle.org/distributions/gradle-{version}-bin.zip.sha256")[1].decode().strip()
        # The second run lets the new Gradle write its own wrapper jar and scripts. Running any task
        # loads every build script, whose bumped plugins only get recorded by the regeneration that
        # follows, so verification is off here; the distribution itself is still checked against sha256.
        for _ in range(2):
            run(
                "./gradlew", "-q", "--dependency-verification", "off", "wrapper",
                "--gradle-version", version, "--gradle-distribution-sha256-sum", sha256,
            )
    pins.append(Pin(
        "gradle", current_gradle,
        lambda: json.loads(get("https://services.gradle.org/versions/current")[1])["version"],
        bump_gradle, regenerate=True,
    ))

    current_jdk = read(CI, r'java-version: "([^"]+)"')
    major = int(current_jdk.split(".")[0])

    def latest_jdk():
        url = (
            "https://api.adoptium.net/v3/info/release_versions?release_type=ga"
            f"&version=%5B{major}%2C{major + 1}%29&page_size=1&sort_order=DESC&vendor=eclipse"
        )
        return json.loads(get(url)[1])["versions"][0]["openjdk_version"].removesuffix("-LTS")

    def bump_jdk(version):
        for workflow in (CI, RELEASE):
            replace(workflow, f'java-version: "{current_jdk}"', f'java-version: "{version}"')
    pins.append(Pin("jdk", current_jdk, latest_jdk, bump_jdk))

    # None of the SDK packages is a Gradle dependency, so bumping them leaves the verification metadata alone.
    runtime = ROOT / "core/runtime/build.gradle.kts"
    places = {
        "ndk": [(CONVENTIONS, 'ndkVersion = "{}"'), (VERIFY_PAGE, "ndk;{}")],
        "cmake": [(runtime, 'version = "{}"'), (CI, "cmake;{}"), (RELEASE, "cmake;{}"), (VERIFY_PAGE, "cmake;{}")],
        "build-tools": [
            (CONVENTIONS, 'buildToolsVersion = "{}"'), (RELEASE, "build-tools/{}/apksigner"), (VERIFY_PAGE, "build-tools;{}"),
        ],
    }
    for kind, files in places.items():
        path, template = files[0]
        current = read(path, re.escape(template).replace(r"\{\}", r'([^"]+)'))

        def bump(version, files=files, current=current):
            for file, pattern in files:
                replace(file, pattern.format(current), pattern.format(version))
        pins.append(Pin(kind, current, lambda k=kind, c=current: newest(stable_sdk_packages()[k], c), bump))

    protoc_build = ROOT / "core/xray/build.gradle.kts"
    current_protoc = read(protoc_build, r'"com\.google\.protobuf:protoc:([^"]+)"')
    pins.append(Pin(
        "protoc", current_protoc,
        # grpc-protobuf-lite needs protoc's 3.x javalite runtime.
        lambda: newest(library_versions("com.google.protobuf", "protoc"), current_protoc, major="3"),
        lambda v: replace(protoc_build, f'protoc:{current_protoc}"', f'protoc:{v}"'), regenerate=True,
    ))
    return pins


def core_pins():
    xray = (ROOT / "third_party/xray/VERSION").read_text().strip()
    geodata = ROOT / "third_party/geodata"
    geoip, geosite = ((geodata / f).read_text().strip() for f in ("GEOIP_VERSION", "GEOSITE_VERSION"))
    ca_bundle = (ROOT / "third_party/mozilla-ca-bundle/VERSION").read_text().strip()

    def pinned(file):
        return (geodata / file).read_text().strip()

    def latest_ca_bundle():
        return max(re.findall(r"cacert-(\d{4}-\d{2}-\d{2})\.pem", get("https://curl.se/docs/caextract.html")[1].decode()))
    return [
        Pin("xray", xray, lambda: latest_github_release("XTLS/Xray-core"), lambda v: run("./scripts/change-xray-ver.sh", v)),
        # The script pins both geodata files at once, so each bump keeps the other one's current tag.
        Pin("geoip", geoip, lambda: latest_github_release("v2fly/geoip"),
            lambda v: run("./scripts/update-geodata.sh", v, pinned("GEOSITE_VERSION"))),
        Pin("geosite", geosite, lambda: latest_github_release("v2fly/domain-list-community"),
            lambda v: run("./scripts/update-geodata.sh", pinned("GEOIP_VERSION"), v)),
        Pin("ca-bundle", ca_bundle, latest_ca_bundle, lambda v: run("./scripts/update-ca-bundle.sh", v)),
    ]


def action_pins():
    used = {}
    for workflow in WORKFLOWS:
        for repository, sha, tag in re.findall(r"uses: ([\w.-]+/[\w.-]+)@([0-9a-f]{40}) # (\S+)", workflow.read_text()):
            used[repository] = (sha, tag)
    pins = []
    for repository, (sha, tag) in sorted(used.items()):
        tags = {}

        def find_latest(repository=repository, tag=tag, tags=tags):
            # Not every action publishes GitHub releases, but all of them tag vX.Y.Z.
            tags.update({t: c for t, c in github_tags(repository).items() if re.fullmatch(r"v\d+\.\d+\.\d+", t)})
            return max(list(tags) + [tag], key=version_key)

        def bump(version, repository=repository, sha=sha, tag=tag, tags=tags):
            for workflow in WORKFLOWS:
                text = workflow.read_text()
                workflow.write_text(text.replace(f"{repository}@{sha} # {tag}", f"{repository}@{tags[version]} # {version}"))
        pins.append(Pin(repository, tag, find_latest, bump))
    return pins


GROUPS = {
    "libraries": ("Libraries", library_pins),
    "build": ("Build suite", build_pins),
    "core": ("Core", core_pins),
    "actions": ("Actions", action_pins),
}


def add_foreign_checksums():
    text = METADATA.read_text()
    for group, name, repository, classifiers, extension in FOREIGN:
        head_pattern = rf'      <component group="{re.escape(group)}" name="{re.escape(name)}" version="([^"]+)">\n'
        for version in re.findall(head_pattern, text):
            head = f'      <component group="{group}" name="{name}" version="{version}">\n'
            start = text.index(head) + len(head)
            end = text.index("      </component>\n", start)
            blocks = re.findall(r'         <artifact name="[^"]+">\n(?:            <sha256 [^\n]+\n)+         </artifact>\n', text[start:end])
            present = {re.search(r'name="([^"]+)"', block).group(1) for block in blocks}
            for classifier in classifiers:
                file = f"{name}-{version}-{classifier}.{extension}"
                if file in present:
                    continue
                url = f"{repository}/{group.replace('.', '/')}/{name}/{version}/{file}"
                data = get(url)[1]
                if hashlib.sha1(data).hexdigest() != get(f"{url}.sha1")[1].decode().split()[0]:
                    raise RuntimeError(f"{file} does not match its repository's SHA-1")
                print(f"  recorded {file}")
                blocks.append(
                    f'         <artifact name="{file}">\n'
                    f'            <sha256 value="{hashlib.sha256(data).hexdigest()}" origin="Checked against the repository\'s SHA-1"/>\n'
                    f"         </artifact>\n"
                )
            blocks.sort(key=lambda block: re.search(r'name="([^"]+)"', block).group(1))
            text = text[:start] + "".join(blocks) + text[end:]
    METADATA.write_text(text)


def regenerate():
    print("Regenerating gradle/verification-metadata.xml...")
    failed = subprocess.run(REGENERATE, cwd=ROOT).returncode != 0
    add_foreign_checksums()
    if failed:
        print("\nThe build had failures (see above). The metadata still covers every task that ran; fix them and run the QA.")


def describe(pin):
    if pin.error:
        return f"could not check: {pin.error}"
    if pin.latest == pin.current:
        return "up to date"
    held = f"  (held: {pin.held})" if pin.held else ""
    return f"{pin.current} -> {pin.latest}{held}"


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("pins", nargs="*", help="groups (libraries, build, core, actions) or pin names (default: all)")
    parser.add_argument("--check", action="store_true", help="report only")
    parser.add_argument("--yes", action="store_true", help="bump without asking")
    parser.add_argument("--regenerate", action="store_true", help="only regenerate gradle/verification-metadata.xml")
    args = parser.parse_args()
    if args.regenerate:
        regenerate()
        return

    wanted = set(args.pins)
    selected = []
    for key, (title, list_pins) in GROUPS.items():
        pins = [p for p in list_pins() if not wanted or key in wanted or p.name in wanted]
        if pins:
            selected.append((title, pins))
    unknown = wanted - set(GROUPS) - {p.name for _, pins in selected for p in pins}
    if unknown:
        sys.exit(f"Unknown pins: {', '.join(sorted(unknown))}")

    # On a terminal each pin shows as "name..." while it's checked, then the line is rewritten with the result.
    live = sys.stdout.isatty()
    for index, (title, pins) in enumerate(selected):
        print(("\n" if index else "") + title)
        width = max(len(p.name) for p in pins)
        for pin in pins:
            if live:
                print(f"  {pin.name}...", end="", flush=True)
            try:
                pin.latest = pin.find_latest()
            except Exception as error:  # One unreachable upstream shouldn't stop the other checks.
                pin.error = error
            print(f"{CLEAR_LINE if live else ''}  {pin.name:<{width}}  {describe(pin)}")

    checked = [p for _, pins in selected for p in pins]
    outdated = [p for p in checked if p.outdated]
    if not outdated or args.check:
        sys.exit(1 if any(p.error for p in checked) else 0)
    if not args.yes:
        if not sys.stdin.isatty() or input(f"\nBump {len(outdated)} pin(s)? [y/N] ").strip().lower() != "y":
            sys.exit(0)

    print()
    for pin in outdated:
        print(f"Bumping {pin.name} {pin.current} -> {pin.latest}")
        pin.bump(pin.latest)
    if any(p.regenerate for p in outdated):
        regenerate()
    print("\nDone. Review `git diff` (especially gradle/verification-metadata.xml) and run the QA from AGENTS.md.")


if __name__ == "__main__":
    main()
