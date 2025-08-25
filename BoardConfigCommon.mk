#
# Copyright (C) 2024 The LineageOS Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

COMMON_PATH := device/samsung/sm7225-common

BUILD_BROKEN_DUP_RULES := true
BUILD_BROKEN_DUP_SYSPROP := true
BUILD_BROKEN_ELF_PREBUILT_PRODUCT_COPY_FILES := true
BUILD_BROKEN_INCORRECT_PARTITION_IMAGES := true

# A/B
 AB_OTA_UPDATER := false

# APEX image
DEXPREOPT_GENERATE_APEX_IMAGE := true

# Android Verified Boot
BOARD_AVB_ENABLE := true
BOARD_AVB_MAKE_VBMETA_IMAGE_ARGS += --flags 3
BOARD_AVB_ROLLBACK_INDEX := $(PLATFORM_SECURITY_PATCH_TIMESTAMP)
BOARD_AVB_RECOVERY_KEY_PATH := external/avb/test/data/testkey_rsa4096.pem
BOARD_AVB_RECOVERY_ALGORITHM := SHA256_RSA4096
BOARD_AVB_RECOVERY_ROLLBACK_INDEX := $(PLATFORM_SECURITY_PATCH_TIMESTAMP)
BOARD_AVB_RECOVERY_ROLLBACK_INDEX_LOCATION := 1

# Platform
PRODUCT_PLATFORM := lito
TARGET_BOARD_PLATFORM := lito
TARGET_BOOTLOADER_BOARD_NAME := lito

TARGET_NO_BOOTLOADER := true

# Init
TARGET_INIT_VENDOR_LIB := //$(COMMON_PATH):libinit_sm7225

# Architecture
TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-2a
TARGET_CPU_ABI := arm64-v8a
TARGET_CPU_ABI2 :=
TARGET_CPU_VARIANT := generic
TARGET_CPU_VARIANT_RUNTIME := cortex-a76

# 2nd Architecture
TARGET_2ND_ARCH := arm
TARGET_2ND_ARCH_VARIANT := armv8-2a
TARGET_2ND_CPU_ABI := armeabi-v7a
TARGET_2ND_CPU_ABI2 := armeabi
TARGET_2ND_CPU_VARIANT := generic
TARGET_2ND_CPU_VARIANT_RUNTIME := cortex-a55

# Kernel
TARGET_KERNEL_SOURCE        := kernel/samsung/m23xq
TARGET_KERNEL_ARCH          := arm64
TARGET_KERNEL_HEADER_ARCH   := arm64
TARGET_LINUX_KERNEL_VERSION := 4.19
TARGET_KERNEL_ADDITIONAL_FLAGS := \
    LLVM=1 \
    LLVM_IAS=1

# Kernel flags
BOARD_KERNEL_CMDLINE += console=null androidboot.hardware=qcom androidboot.memcg=1 lpm_levels.sleep_disabled=1 video=vfb:640x400,bpp=32,memsize=3072000 msm_rtb.filter=0x237 service_locator.enable=1 androidboot.usbcontroller=a600000.dwc3 swiotlb=2048 cgroup.memory=nokmem,nosocket firmware_class.path=/vendor/firmware_mnt/image loop.max_part=7 androidboot.bootdevice=1d84000.ufshc androidboot.fstab_suffix=default androidboot.boot_devices=soc/1d84000.ufshc
BOARD_KERNEL_CMDLINE += androidboot.selinux=permissive
BOARD_BOOTIMG_HEADER_VERSION := 2

BOARD_KERNEL_BASE            := 0x00000000
BOARD_KERNEL_PAGESIZE        := 4096
BOARD_RAMDISK_OFFSET         := 0x02000000
BOARD_KERNEL_OFFSET          := 0x00008000
BOARD_KERNEL_TAGS_OFFSET     := 0x01e00000
BOARD_KERNEL_IMAGE_NAME      := Image
BOARD_KERNEL_SEPARATED_DTBO  := true
BOARD_INCLUDE_DTB_IN_BOOTIMG := true

# Kernel: mkbootimgs args
BOARD_CUSTOM_BOOTIMG := true
BOARD_MKBOOTIMG_ARGS += --ramdisk_offset $(BOARD_RAMDISK_OFFSET)
BOARD_MKBOOTIMG_ARGS += --tags_offset $(BOARD_KERNEL_TAGS_OFFSET)
BOARD_MKBOOTIMG_ARGS += --pagesize $(BOARD_KERNEL_PAGESIZE)
BOARD_MKBOOTIMG_ARGS += --header_version $(BOARD_BOOTIMG_HEADER_VERSION)
BOARD_MKBOOTIMG_ARGS += --board $(BOARD_NAME)
BOARD_MKBOOTIMG_ARGS += --kernel_offset $(BOARD_KERNEL_OFFSET)

# Kernel Clang Flags
KERNEL_CC := CC=clang
override KERNEL_TOOLCHAIN_PREFIX_arm := arm-linux-android-

# Enable LZ4 compression for ramdisks
BOARD_RAMDISK_USE_LZ4 := true

# Additional root folders
TARGET_FS_CONFIG_GEN := $(COMMON_PATH)/config.fs

BOARD_ROOT_EXTRA_FOLDERS += \
    metadata \
    efs

# File systems
BOARD_USERDATAIMAGE_FILE_SYSTEM_TYPE := f2fs
BOARD_VENDORIMAGE_FILE_SYSTEM_TYPE   := ext4
BOARD_SYSTEMIMAGE_FILE_SYSTEM_TYPE   := ext4
BOARD_PRODUCTIMAGE_FILE_SYSTEM_TYPE  := ext4
BOARD_ODMIMAGE_FILE_SYSTEM_TYPE      := ext4
BOARD_CACHEIMAGE_FILE_SYSTEM_TYPE    := ext4

BOARD_USES_METADATA_PARTITION        := true
TARGET_USERIMAGES_USE_F2FS           := true
TARGET_USERIMAGES_USE_EXT4           := true

# Partition sizes, obtained with blockdev --getsize64
BOARD_DTBOIMG_PARTITION_SIZE       := 8388608
BOARD_BOOTIMAGE_PARTITION_SIZE     := 100663296
BOARD_RECOVERYIMAGE_PARTITION_SIZE := 81788928
BOARD_CACHEIMAGE_PARTITION_SIZE    := 629145600

# Super partition sizes, obtained with fdisk -l /dev/block/dm-[0,1,2,3]
BOARD_SUPER_PARTITION_SIZE                      := 10385096704
BOARD_SUPER_PARTITION_GROUPS                    := samsung_dynamic_partitions
BOARD_SAMSUNG_DYNAMIC_PARTITIONS_PARTITION_LIST := system vendor product odm
BOARD_SAMSUNG_DYNAMIC_PARTITIONS_SIZE           := 9340715008 # BOARD_SUPER_PARTITION_SIZE-4MiB
BOARD_VENDORIMAGE_PARTITION_RESERVED_SIZE       := 400000000
BOARD_ODMIMAGE_PARTITION_RESERVED_SIZE     	:= 50000000
BOARD_SYSTEMIMAGE_PARTITION_RESERVED_SIZE       := 3000000000
BOARD_PRODUCTIMAGE_PARTITION_RESERVED_SIZE      := 500000000
BOARD_SYSTEMIMAGE_EXTFS_INODE_COUNT             := -1
BOARD_VENDORIMAGE_EXTFS_INODE_COUNT             := -1
BOARD_PRODUCTIMAGE_EXTFS_INODE_COUNT            := -1
BOARD_ODMIMAGE_EXTFS_INODE_COUNT           	  := -1

BOARD_FLASH_BLOCK_SIZE := 131072 # (BOARD_KERNEL_PAGESIZE * 64)

# Out dirs
TARGET_COPY_OUT_VENDOR := vendor
TARGET_COPY_OUT_PRODUCT := product
TARGET_COPY_OUT_ODM := odm

# Audio policy
USE_CUSTOM_AUDIO_POLICY := 1
USE_XML_AUDIO_POLICY_CONF := 1

# Audio
AUDIO_FEATURE_ENABLED_EXT_AMPLIFIER := true
AUDIO_FEATURE_ENABLED_EXTENDED_COMPRESS_FORMAT := true
AUDIO_FEATURE_ENABLED_GEF_SUPPORT := true
AUDIO_FEATURE_ENABLED_INSTANCE_ID := true
AUDIO_FEATURE_ENABLED_PROXY_DEVICE := true
AUDIO_FEATURE_ENABLED_SSR := true
BOARD_SUPPORTS_SOUND_TRIGGER := true
BOARD_SUPPORTS_OPENSOURCE_STHAL := true
TARGET_PROVIDES_AUDIO_EXTNS := true

# ANT+
BOARD_ANT_WIRELESS_DEVICE := "qualcomm-hidl"

# Camera
SOONG_CONFIG_NAMESPACES += samsungCameraVars
SOONG_CONFIG_samsungCameraVars += needs_sec_reserved_field

SOONG_CONFIG_samsungCameraVars_needs_sec_reserved_field := true

# Keymaster
TARGET_KEYMASTER_VARIANT := samsung

# Lights
$(call soong_config_set,samsungVars,target_specific_header_path,$(COMMON_PATH)/include)

# Lineage Health
TARGET_HEALTH_CHARGING_CONTROL_CHARGING_PATH := /sys/class/power_supply/battery/batt_slate_mode
TARGET_HEALTH_CHARGING_CONTROL_CHARGING_ENABLED := 0
TARGET_HEALTH_CHARGING_CONTROL_CHARGING_DISABLED := 1
TARGET_HEALTH_CHARGING_CONTROL_SUPPORTS_BYPASS := false

# HIDL manifests
DEVICE_MANIFEST_FILE += $(COMMON_PATH)/configs/manifest.xml
DEVICE_FRAMEWORK_COMPATIBILITY_MATRIX_FILE += \
    $(COMMON_PATH)/configs/framework_compatibility_matrix.xml \
    hardware/samsung/vintf/samsung_framework_compatibility_matrix.xml \
    hardware/qcom-caf/common/vendor_framework_compatibility_matrix.xml \
    vendor/lineage/config/device_framework_matrix.xml
DEVICE_MATRIX_FILE += \
    hardware/qcom-caf/common/compatibility_matrix.xml

# Qcom Hardware
BOARD_USES_QCOM_HARDWARE := true

# Board Vendor
BOARD_VENDOR := samsung

# DRM
TARGET_ENABLE_MEDIADRM_64 := true

VENDOR_SECURITY_PATCH := 2025-02-01

# FM
BOARD_HAS_QCA_FM_SOC := cherokee
BOARD_HAVE_QCOM_FM := true

# GPU
TARGET_BOARD_PLATFORM_GPU := qcom-adreno619

# Graphics
ANDROID_ENABLE_RENDERSCRIPT := true
MAX_EGL_CACHE_KEY_SIZE := 12*1024
MAX_EGL_CACHE_SIZE := 2048*1024
NUM_FRAMEBUFFER_SURFACE_BUFFERS := 3
SF_VSYNC_EVENT_PHASE_OFFSET_NS := 6000000
TARGET_FORCE_HWC_FOR_VIRTUAL_DISPLAYS := true
TARGET_HARDWARE_3D := true
TARGET_HAS_HDR_DISPLAY := false
TARGET_HAS_WIDE_COLOR_DISPLAY := true
TARGET_USES_C2D_COMPOSITION := true
TARGET_USES_COLOR_METADATA := true
TARGET_USES_DISPLAY_RENDER_INTENTS := true
TARGET_USES_DRM_PP := true
TARGET_USES_GRALLOC1 := true
TARGET_USES_GRALLOC4 := true
TARGET_USES_HWC2 := true
TARGET_USES_ION := true
USE_OPENGL_RENDERER := true
VSYNC_EVENT_PHASE_OFFSET_NS := 2000000

# RIL
ENABLE_VENDOR_RIL_SERVICE := true

# Recovery
BOARD_HAS_DOWNLOAD_MODE := true
BOARD_INCLUDE_RECOVERY_DTBO := true
TARGET_RECOVERY_PIXEL_FORMAT := RGBX_8888
TARGET_USES_MKE2FS := true
BOARD_USES_FULL_RECOVERY_IMAGE := true
TARGET_RECOVERY_FSTAB := $(COMMON_PATH)/recovery/root/fstab.default

# SePolicy
include device/qcom/sepolicy_vndr/SEPolicy.mk
include device/lineage/sepolicy/libperfmgr/sepolicy.mk

BOARD_VENDOR_SEPOLICY_DIRS += $(COMMON_PATH)/sepolicy/vendor
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(COMMON_PATH)/sepolicy/private
SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += $(COMMON_PATH)/sepolicy/public

# Vibrator
$(call soong_config_set,samsungVibratorVars,duration_amplitude,true)

# Wifi
BOARD_WLAN_DEVICE := qcwcn
BOARD_HOSTAPD_DRIVER := NL80211
BOARD_HOSTAPD_PRIVATE_LIB := lib_driver_cmd_$(BOARD_WLAN_DEVICE)
BOARD_WPA_SUPPLICANT_DRIVER := NL80211
BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_$(BOARD_WLAN_DEVICE)
QC_WIFI_HIDL_FEATURE_DUAL_AP := true
WIFI_DRIVER_DEFAULT := qca_cld3
WIFI_HIDL_FEATURE_AWARE := true
WIFI_HIDL_FEATURE_DUAL_INTERFACE := true
WIFI_HIDL_UNIFIED_SUPPLICANT_SERVICE_RC_ENTRY := true
WPA_SUPPLICANT_VERSION := VER_0_8_X
