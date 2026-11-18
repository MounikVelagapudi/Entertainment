package com.example.entertainment.pos.domain

/** Hardware seam. The real thing would wrap CameraX + ML Kit or a vendor scanner SDK. */
interface BarcodeScanner {
    suspend fun scan(): String
}
