package com.pkos.roadeye.model

import android.graphics.Bitmap
import java.time.LocalDateTime

data class Detection(val label: String, val detection_time: LocalDateTime, val image: Bitmap?) {
}