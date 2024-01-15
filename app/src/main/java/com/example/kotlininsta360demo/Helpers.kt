package com.example.kotlininsta360demo


    fun formatSize(size: Long): String {
        var size = size
        var suffix: String? = null
        if (size >= 1024) {
            suffix = "KiB"
            size /= 1024
            if (size >= 1024) {
                suffix = "MiB"
                size /= 1024
                if (size >= 1024) {
                    suffix = "GiB"
                    size /= 1024
                }
            }
        }
        val resultBuffer = StringBuilder(java.lang.Long.toString(size))
        var commaOffset = resultBuffer.length - 3
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, ',')
            commaOffset -= 3
        }
        if (suffix != null) resultBuffer.append(suffix)
        return resultBuffer.toString()
    }
