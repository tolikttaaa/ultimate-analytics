package com.ttaaa.ultimate.analysis

/**
 * Version of the analysis algorithms and the `AnalysisParameters` defaults, stored with every analysed session.
 * A session is outdated when its version is lower (spec 8.2). Bump it whenever an algorithm or a default changes,
 * and record the new defaults in `AnalysisVersionTest`.
 */
const val ANALYSIS_VERSION = 1
