package com.componentvault.android.data.bom

import java.security.MessageDigest

object ImportHash {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02X".format(byte) }
}
