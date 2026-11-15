package com.material.xray.core.xray

/**
 * How a core process ended, reduced to values that carry nothing from its log or config, so they
 * can be reported as they are.
 */
data class CoreExit(
    val status: Status,
    val logMarker: LogMarker,
) {
    sealed interface Status {
        data class Exited(val code: Int) : Status

        data class Signaled(val signal: Int) : Status

        /** Nothing collected the status: a root core is not a child of this process, or something else reaped it. */
        data object Unknown : Status
    }

    /** The Go runtime's last words, which it writes to stderr ahead of any traceback. */
    enum class LogMarker {
        GoPanic,
        GoOutOfMemory,
        GoFatalError,
        None,
        ;

        companion object {
            /** An extended regular expression for the lines [of] tells apart, for `grep -E`. */
            const val LINE_PATTERN = "^(panic|fatal error): "

            // The runtime words the same failure differently depending on where allocation failed.
            private val OUT_OF_MEMORY = listOf("out of memory", "cannot allocate memory")

            fun of(line: String?): LogMarker = when {
                line == null -> None
                line.startsWith("fatal error: ") && OUT_OF_MEMORY.any(line::contains) -> GoOutOfMemory
                line.startsWith("fatal error: ") -> GoFatalError
                line.startsWith("panic: ") -> GoPanic
                else -> None
            }
        }
    }
}
