#!/usr/bin/env bash
set -euo pipefail

expected_stream_server_commit="368666eb09f9c4849a7df7f2a6a8b26171f24cfb"
expected_vcpkg_commit="84bab45d415d22042bd0b9081aea57f362da3f35"
abi="${STREAM_SERVER_ABI:-arm64-v8a}"
case "$abi" in
  arm64-v8a)
    rust_target="aarch64-linux-android"
    vcpkg_triplet="arm64-android"
    vcpkg_arch="arm64"
    android_abi="arm64-v8a"
    make_host="aarch64-linux-android"
    ;;
  armeabi-v7a)
    rust_target="armv7-linux-androideabi"
    vcpkg_triplet="arm-android"
    vcpkg_arch="arm"
    android_abi="armeabi-v7a"
    make_host="armv7a-linux-androideabi"
    ;;
  *)
    printf 'Unsupported stream-server Android ABI: %s\n' "$abi" >&2
    exit 1
    ;;
esac
stream_server_root="${STREAM_SERVER_ROOT:-$(cd "$(dirname "$0")/../../stream-server" && pwd)}"
vcpkg_root="${VCPKG_ROOT:-}"
installed_root="${VCPKG_INSTALLED_DIR:-}"
ndk_root="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

fail() {
  printf 'stream-server ARM64 build prerequisite error: %s\n' "$1" >&2
  exit 1
}

[[ -n "$vcpkg_root" ]] || fail 'set VCPKG_ROOT to an existing vcpkg checkout (the release workflow pins vcpkg commit 84bab45d415d22042bd0b9081aea57f362da3f35).'
[[ -x "$vcpkg_root/vcpkg" ]] || fail "VCPKG_ROOT must contain an executable vcpkg at $vcpkg_root/vcpkg."
actual_vcpkg_commit="$(git -C "$vcpkg_root" rev-parse HEAD 2>/dev/null)" || fail 'VCPKG_ROOT must be a Git checkout pinned to the release vcpkg revision.'
[[ "$actual_vcpkg_commit" == "$expected_vcpkg_commit" ]] || \
  fail "expected vcpkg commit $expected_vcpkg_commit, found $actual_vcpkg_commit."
[[ -n "$installed_root" ]] || fail 'set VCPKG_INSTALLED_DIR or run through the matching Android native build task.'
[[ -n "$ndk_root" && -d "$ndk_root" ]] || fail 'set ANDROID_NDK_HOME to an installed Android NDK directory.'
[[ -f "$stream_server_root/server/Cargo.toml" ]] || fail "stream-server checkout is missing at $stream_server_root."

actual_stream_server_commit="$(git -C "$stream_server_root" rev-parse HEAD)"
[[ "$actual_stream_server_commit" == "$expected_stream_server_commit" ]] || \
  fail "expected pinned stream-server $expected_stream_server_commit, found $actual_stream_server_commit; do not update the submodule pointer for this build."

for tool in cargo rustc pkg-config cmake ninja file; do
  command -v "$tool" >/dev/null 2>&1 || fail "'$tool' is required on PATH; install/configure it before requesting the native task."
done
command -v rustup >/dev/null 2>&1 || fail "'rustup' is required to manage the Android Rust target."
cargo ndk --version >/dev/null 2>&1 || fail "cargo-ndk is required; install it with 'cargo install cargo-ndk'."
rustup target list --installed 2>/dev/null | grep -qx "$rust_target" || \
  fail "install the Rust Android target with 'rustup target add $rust_target'."

mkdir -p "$installed_root"
triplet_dir="$(mktemp -d "${TMPDIR:-/tmp}/stremio-android-triplet.XXXXXX")"
trap 'rm -rf "$triplet_dir"' EXIT
cat > "$triplet_dir/$vcpkg_triplet.cmake" <<EOF
set(VCPKG_TARGET_ARCHITECTURE $vcpkg_arch)
set(VCPKG_CRT_LINKAGE dynamic)
set(VCPKG_LIBRARY_LINKAGE static)
set(VCPKG_CMAKE_SYSTEM_NAME Android)
set(VCPKG_CMAKE_SYSTEM_VERSION 24)
set(VCPKG_MAKE_BUILD_TRIPLET "--host=$make_host")
EOF
if [[ "$abi" == "armeabi-v7a" ]]; then
  echo 'set(VCPKG_CMAKE_CONFIGURE_OPTIONS -DANDROID_ABI=armeabi-v7a -DANDROID_ARM_NEON=ON)' >> "$triplet_dir/$vcpkg_triplet.cmake"
else
  echo "set(VCPKG_CMAKE_CONFIGURE_OPTIONS -DANDROID_ABI=$android_abi)" >> "$triplet_dir/$vcpkg_triplet.cmake"
fi

"$vcpkg_root/vcpkg" install \
  "--x-manifest-root=$stream_server_root" \
  --triplet "$vcpkg_triplet" \
  "--x-install-root=$installed_root" \
  "--overlay-triplets=$triplet_dir" \
  "--overlay-ports=$stream_server_root/vcpkg-overlays"

openssl_root="$installed_root/$vcpkg_triplet"
[[ -d "$openssl_root/include/openssl" ]] || fail "Android $abi OpenSSL headers were not produced under $openssl_root."
[[ -d "$openssl_root/lib" ]] || fail "Android $abi dependency libraries were not produced under $openssl_root."

export ANDROID_NDK_HOME="$ndk_root"
export VCPKG_ROOT="$vcpkg_root"
export VCPKG_INSTALLED_DIR="$installed_root"
export VCPKGRS_TRIPLET="$vcpkg_triplet"
export PKG_CONFIG_ALLOW_CROSS=1
export PKG_CONFIG_PATH="$openssl_root/lib/pkgconfig"
export PKG_CONFIG_SYSROOT_DIR="$openssl_root"
export OPENSSL_DIR="$openssl_root"

printf 'Building stream-server commit %s for %s (API 24, release, libtorrent).\n' "$actual_stream_server_commit" "$rust_target"
(cd "$stream_server_root/server" && cargo ndk --target "$rust_target" --platform 24 build --release --features libtorrent --no-default-features)

library="$stream_server_root/target/$rust_target/release/libstream_server.so"
[[ -s "$library" ]] || fail "cargo completed without producing $library."
library_type="$(file "$library")"
if [[ "$abi" == "armeabi-v7a" ]]; then
  [[ "$library_type" == *"ELF 32-bit"* && "$library_type" == *"ARM"* && "$library_type" == *"dynamically linked"* ]] || \
    fail "native output is not a 32-bit ARM Android ELF shared object: $library_type"
else
  [[ "$library_type" == *"ELF 64-bit"* && "$library_type" == *"ARM aarch64"* && "$library_type" == *"dynamically linked"* ]] || \
    fail "native output is not an ARM64 Android ELF shared object: $library_type"
fi
printf 'Verified native ABI %s: %s\n' "$abi" "$library_type"
printf 'Built native library: %s\n' "$library"
