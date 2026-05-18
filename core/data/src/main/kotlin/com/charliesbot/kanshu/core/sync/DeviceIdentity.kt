package com.charliesbot.kanshu.core.sync

// Stable identity passed to the sync server so it can distinguish "this device's last write"
// from other devices' writes. ANDROID_ID is stable per app-signing per user and survives
// app updates; resetting it requires a factory reset, which is acceptable for our use case.
//
// `name` is what shows up in the prompt ("Continue from page X on (Boox Go 7)?"). No PII;
// just the OEM model string.
data class DeviceIdentity(val id: String, val name: String)
