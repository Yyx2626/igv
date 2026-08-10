# Customized IGV changelog

This changelog records customization and debugging work in this fork. It does
not duplicate routine changes made by the upstream `igvteam/igv` project.

## August 10, 2026

- Removed hosted-genome, OAuth, and track-hub network discovery from main-window
  construction so the interface appears promptly without Wi-Fi.
- Made local FASTA loading stay local: local genome registration no longer
  triggers hosted-genome or UCSC hub queries.
- Cached downloaded default RefSeq annotations under the user's IGV directory,
  with bounded timeouts, stoppable downloads, atomic cache publication, and
  correct non-indexed reading of ordinary gzip tables.
- Kept the genome-loading dialog visible through default RefSeq loading and
  changed Stop into normal cancellation without a false UI `SEVERE` error.
- Restored saved main-window bounds at no smaller than the preferred startup
  size while keeping the window within the selected display.
- Used temporary startup timing checkpoints to isolate network delays, then
  removed them; bypassed URL mapping for local services and limited the optional
  remote mapping-table lookup to one attempt per run.
- Excluded compiled distribution packages from Git tracking to prevent release
  archives and bundled JDKs from inflating repository history.

## August 9, 2026

- Forked the customized workspace to `Yyx2626/igv`, documented its upstream
  origin, and established GitHub Releases as the compiled-build download page.
- Merged the latest upstream changes available that day into the customized
  branch.
- Fixed paired Average With Error Bar placement so top and bottom averages stay
  adjacent, retain top-above-bottom order, and replace selected tracks at the
  first selected track's visible position.
- Kept default RefSeq annotation near the top while making interactively loaded
  files appear progressively in load order.
- Added a stoppable genome-loading dialog that remains visible until default
  RefSeq annotation loading finishes.
- Fixed stale genome-selector updates and prevented a stopped or superseded
  genome request from overwriting a newer selection.
- Restored the global HTTP timeout and UCSC fallback behavior used by the last
  stable customized build.
- Set the source default track-divider height to 5 pixels with a light-gray
  color matching the default track background.
- Fixed release metadata processing so compiled builds contain their actual
  version and timestamp rather than unresolved placeholders.

## August 7, 2026

- Expanded Average With Error Bar options for SEM, SD, missing values, windowing,
  rendering, colors, cap style, and restoration of original tracks.
- Fixed session restoration for average and merged tracks, pair links, pair-group
  columns, group tabs, track order, and saved data ranges.
- Added live global and per-track controls for track backgrounds, dividers,
  rulers, borders, and data-range mid-lines.
- Fixed negative-only autoscaling, Y-axis label clipping, mid-line clipping,
  viewport background gaps, error-bar autoscaling, and pixel alignment.
- Implemented visually zero-height dividers with a floating hover target that
  remains draggable.
- Made HTTP connect/read timeouts configurable and added startup diagnostics for
  remote-loading delays.
- Fixed `igvtools` launchers and bundled-JDK selection on macOS and Windows.

## August 6, 2026

- Added explicit top/bottom track pairing with shared range editing, independent
  paired autoscaling, and JSON session persistence.
- Added numbered track groups 1–9 with keyboard shortcuts and group-tab controls.
- Added synthetic Average With Error Bar tracks and the initial statistics and
  rendering implementation.
- Fixed selection, context-menu, drag-and-drop, ordering, autoscaling, rendering,
  and session bugs found while developing the new track workflows.
- Rebuilt and corrected the macOS launcher and application template.

## August 5, 2026

- Cloned the upstream `igvteam/igv` repository as the starting point for the
  customization work.
