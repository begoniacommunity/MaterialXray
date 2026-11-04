#!/bin/bash
# Pins the geodata the APK bundles: the v2fly release tags of geoip.dat and geosite.dat (dlc.dat)
# and the SHA-256 of each file, which the downloadGeoData task checks. Without arguments it pins
# the latest releases.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

GEODATA_DIR="third_party/geodata"
GEOIP_REPO="v2fly/geoip"
GEOSITE_REPO="v2fly/domain-list-community"
WORK_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

latest_tag() {
  local url
  url="$(curl -fsSLI -o /dev/null -w '%{url_effective}' "https://github.com/$1/releases/latest")"
  printf '%s\n' "${url##*/}"
}

# Prints the SHA-256 of $3 from release $2 of repository $1 after checking it against upstream's.
verify() {
  local repo="$1" tag="$2" asset="$3"
  local path="${WORK_DIR}/${asset}"
  local expected_sha256 actual_sha256

  if [[ ! "${tag}" =~ ^[0-9]+$ ]]; then
    echo "Invalid ${repo} release tag: ${tag}" >&2
    exit 1
  fi
  echo "Downloading ${repo} ${tag} ${asset}..." >&2
  curl -fsSL "https://github.com/${repo}/releases/download/${tag}/${asset}" -o "${path}"
  curl -fsSL "https://github.com/${repo}/releases/download/${tag}/${asset}.sha256sum" -o "${path}.sha256sum"
  read -r expected_sha256 _ < "${path}.sha256sum"
  read -r actual_sha256 _ < <(sha256sum "${path}")
  if [[ ! "${expected_sha256}" =~ ^[0-9a-f]{64}$ ]] || [[ "${actual_sha256}" != "${expected_sha256}" ]]; then
    echo "SHA-256 mismatch for ${repo} ${tag} ${asset}" >&2
    exit 1
  fi
  if (($(stat -c %s "${path}") <= 1024)); then
    echo "${repo} ${tag} ${asset} is unexpectedly small" >&2
    exit 1
  fi
  printf '%s\n' "${actual_sha256}"
}

GEOIP_TAG="${1:-$(latest_tag "${GEOIP_REPO}")}"
GEOSITE_TAG="${2:-$(latest_tag "${GEOSITE_REPO}")}"
geoip_sha256="$(verify "${GEOIP_REPO}" "${GEOIP_TAG}" geoip.dat)"
geosite_sha256="$(verify "${GEOSITE_REPO}" "${GEOSITE_TAG}" dlc.dat)"

mkdir -p "${GEODATA_DIR}"
printf '%s\n' "${GEOIP_TAG}" > "${GEODATA_DIR}/GEOIP_VERSION"
printf '%s\n' "${GEOSITE_TAG}" > "${GEODATA_DIR}/GEOSITE_VERSION"
printf '%s  %s\n' "${geoip_sha256}" geoip.dat "${geosite_sha256}" geosite.dat > "${GEODATA_DIR}/CHECKSUMS.sha256"

echo "Pinned geoip ${GEOIP_TAG} and geosite ${GEOSITE_TAG}."
