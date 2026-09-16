package com.componentvault.android.model

data class SyncConfiguration(
    val deviceId: String,
    val serverBaseUrl: String,
    val apiToken: String,
    val autoSyncEnabled: Boolean,
    val lastSyncedAt: String,
    val lastSyncMessage: String,
    val externalServerBaseUrl: String = "",
) {
    val apiTokenMasked: String
        get() {
            if (apiToken.isBlank()) {
                return ""
            }
            if (apiToken.length <= 4) {
                return "*".repeat(apiToken.length)
            }
            return "${"*".repeat(apiToken.length - 4)}${apiToken.takeLast(4)}"
        }
}
