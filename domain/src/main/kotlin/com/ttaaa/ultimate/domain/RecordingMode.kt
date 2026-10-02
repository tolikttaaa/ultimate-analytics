package com.ttaaa.ultimate.domain

/**
 * How the watch recorded a session, detected from its record intervals. Spec 4.2 requires [EVERY_SECOND];
 * [SMART] recording (records 1-7 s apart) is analysed with a longer interpolation limit, and its sprint and
 * acceleration metrics are low-confidence (docs/DECISIONS.md).
 */
enum class RecordingMode { EVERY_SECOND, SMART }
