# Notebooks

Kotlin Notebooks for exploring sessions and calibrating `AnalysisParameters` (spec 7.1, milestone M0). They are not
part of the build.

1. Build the library jars: `./gradlew :fit-parser:jar :analysis:jar`.
2. Open a notebook in IntelliJ IDEA with the Kotlin Notebook plugin. Paths are relative to this directory; if the
   notebook's working directory differs, adjust them in the dependency cell.

| Notebook | Purpose |
| --- | --- |
| `session-explorer.ipynb` | Analyse one FIT file and plot the smoothed speed with the detected efforts |

Commit notebooks without outputs.
