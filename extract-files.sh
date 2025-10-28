#!/bin/bash
#
# Copyright (C) 2018-2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

set -e

DEVICE_COMMON=sm7225-common
VENDOR=samsung

# Load extractutils and do some sanity checks
MY_DIR="${BASH_SOURCE%/*}"
if [[ ! -d "$MY_DIR" ]]; then MY_DIR="$PWD"; fi

ANDROID_ROOT="${MY_DIR}/../../.."

HELPER="${ANDROID_ROOT}/tools/extract-utils/extract_utils.sh"
if [ ! -f "${HELPER}" ]; then
    echo "Unable to find helper script at ${HELPER}"
    exit 1
fi
source "${HELPER}"

# Default to sanitizing the vendor folder before extraction
CLEAN_VENDOR=true

KANG=
SECTION=

while [ "${#}" -gt 0 ]; do
    case "${1}" in
        -n | --no-cleanup )
                CLEAN_VENDOR=false
                ;;
        -k | --kang )
                KANG="--kang"
                ;;
        -s | --section )
                SECTION="${2}"; shift
                CLEAN_VENDOR=false
                ;;
        * )
                SRC="${1}"
                ;;
    esac
    shift
done

if [ -z "${SRC}" ]; then
    SRC="adb"
fi

function blob_fixup() {
    case "${1}" in
        vendor/lib64/libsec-ril.so)
	    [ "$2" = "" ] && return 0
            # Replace SlotID prop
            sed -i 's/ril.dds.call.ongoing/vendor.calls.slot_id/g' "${2}"
            # Pass an empty value to SecRil::RequestComplete in OnGetSmscAddressDone (mov x3,x20 -> mov,x3,#0x0)
            xxd -p -c0 "${2}" | sed "s/600e40f9820c805224008052e10315aa080040f9e30314aa/600e40f9820c805224008052e10315aa080040f9030080d2/g" | xxd -r -p > "${2}".patched
            mv "${2}".patched "${2}"
            ;;
	vendor/lib64/hw/gatekeeper.mdfpp.so|vendor/lib64/libskeymaster4device.so)
            "${PATCHELF}" --replace-needed "libcrypto.so" "libcrypto-v33.so" "${2}"
            ;;
        vendor/lib/unihal_main@2.15.so|vendor/lib64/unihal_main@2.15.so)
            [ "$2" = "" ] && return 0
            "${PATCHELF}" --add-needed "libui_shim.so" "${2}"
            ;;
	vendor/lib64/nfc_nci_nxpsn.so)
            [ "$2" = "" ] && return 0
            "${PATCHELF}" --add-needed "libbase_shim.so" "${2}"
            ;;
	*)
            return 1
            ;;
    esac
}

# Initialize the helper
setup_vendor "${DEVICE_COMMON}" "${VENDOR}" "${ANDROID_ROOT}" true "${CLEAN_VENDOR}"

extract "${MY_DIR}/proprietary-files.txt" "${SRC}" "${KANG}" --section "${SECTION}"

# Fix proprietary blobs
BLOB_ROOT="$ANDROID_ROOT"/vendor/"$VENDOR"/"$DEVICE_COMMON"/proprietary

"${MY_DIR}/setup-makefiles.sh"
