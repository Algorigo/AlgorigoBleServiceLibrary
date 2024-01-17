package com.algorigo.algorigobleservicelibrary.util

import java.nio.ByteBuffer

fun Long.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(Long.SIZE_BYTES)
    buffer.putLong(this)
    return buffer.array()
}
