package com.example.casto

/**
 * A single HTTP byte range (`Range: bytes=start-end`) resolved against a total size.
 */
data class HttpRange(val start: Long, val end: Long) {

    val length: Long get() = end - start + 1

    companion object {

        /**
         * Parses an HTTP `Range` header. Returns `null` when the header is absent,
         * malformed or not satisfiable; the caller should then serve the full content.
         */
        fun parse(header: String?, total: Long): HttpRange? {
            if (total <= 0L || header == null) return null

            val value = header.trim()
            if (!value.startsWith("bytes=", ignoreCase = true)) return null

            // Only the first range is honoured.
            val spec = value.substring("bytes=".length).substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash < 0) return null

            val startPart = spec.substring(0, dash).trim()
            val endPart = spec.substring(dash + 1).trim()

            return try {
                if (startPart.isEmpty()) {
                    // Suffix range: the last N bytes.
                    val suffix = endPart.toLong()
                    if (suffix <= 0L) null else HttpRange(maxOf(0L, total - suffix), total - 1)
                } else {
                    val start = startPart.toLong()
                    val end = if (endPart.isEmpty()) total - 1 else minOf(endPart.toLong(), total - 1)
                    if (start < 0L || start >= total || start > end) null else HttpRange(start, end)
                }
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}
