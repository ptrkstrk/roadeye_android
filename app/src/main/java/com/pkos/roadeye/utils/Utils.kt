package com.pkos.roadeye.utils

object Utils {
    fun convertLabel(label: String): String {
        println(label)
        var fixedLabel = label.replace('-', ' ')
        fixedLabel = fixedLabel.replace("  ", " ")
        fixedLabel = fixedLabel.removeRange(fixedLabel.length-3,fixedLabel.length)
        println(fixedLabel)
        return fixedLabel
    }
}