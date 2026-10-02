package com.ttaaa.ultimate.fit

/** Why a file cannot be used as a session (spec 11: such files are reported as FAILED with a reason). */
sealed class FitParseException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class NotAFitFile(cause: Throwable? = null) : FitParseException("Not a FIT file", cause)

    class Corrupted(cause: Throwable? = null) : FitParseException("FIT file is corrupted", cause)

    class NotAnActivity(val fileType: String?) :
        FitParseException("FIT file is ${fileType?.let { "a '$it'" } ?: "an unknown"} file, not an activity")

    class MissingData(val what: String) : FitParseException("FIT activity has no $what")
}
