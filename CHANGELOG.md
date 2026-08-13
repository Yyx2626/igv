# Customized IGV changelog

This changelog records customization and debugging work in this fork. It does
not duplicate routine changes made by the upstream `igvteam/igv` project.

## Known issues / planned work (as of August 13, 2026)

Code for these is written and compiles, but not yet fully verified/tested in
the app or built into a release:

- TSV export: add each Average track member's own raw value as extra columns
  (`Average.source.<member name>`) so `Average.N/average/SD/SEM` can be
  cross-checked against member data without exporting twice - done, needs
  in-app verification.
- TSV export: prefix every column with `track_N.` (display order among
  exported tracks) so column identity is unambiguous - done, needs in-app
  verification.

Not yet started:

- Debug: the "Set Track Height..." dialog (and Preferences > Tracks height)
  sometimes doesn't change a track's visible height after clicking OK.
  Suspect: `TrackPanel.getPreferredSize()`'s `Math.max(track.getHeight(),
  track.getContentHeight())` can silently keep a track at its old (larger)
  content height when shrinking a track whose `getContentHeight()` differs
  from its own `height` field (composite/feature tracks) - needs a concrete
  repro (which track type, shrink vs. enlarge) to confirm before fixing.
- Add a "Change Border Between Pairs" context menu (Set Border Height / Set
  Border Color / Unset) for one or more selected paired tracks, reusing the
  existing per-track `borderHeightOverride`/`borderColorOverride` mechanism
  already exposed via right-clicking a `TrackPanelDivider` directly - applies
  the override to the TOP track of each pair in the selection (resolving
  BOTTOM-selected tracks to their partner via `TrackPairing.findPartner`).
- Debug: undoing "Add Region" (add Region of Interest) may already restore
  the underlying model correctly, but the translucent red ROI bar on screen
  doesn't disappear - likely a missing repaint after the undo, not a broken
  undo action itself.

## August 13, 2026

- Fixed the actual cause of error bars visibly overshooting the gray midline
  in SVG/PDF export: a near-zero-error bin's clamped span could round to less
  than a single pixel, and the old code forced 1px of visible height by
  growing unconditionally downward - past the baseline. Degenerate spans are
  now skipped entirely (matching how `BarChartRenderer` already skips a
  zero-height mean bar) instead of forced visible. (Two earlier attempts this
  session - reclamping `barModeYPixel`'s bound, then matching
  `renderScores()`'s unclamped-reference formula - fixed related but
  different pixel-math bugs without fixing this one; the SVG file's own
  coordinates were compared directly against the midline's to confirm this
  fix.)
- Fixed the SINGLE ("T"-shape) error bar cap being drawn at the wrong end -
  the mean's end instead of the outward tip - for any bin whose mean is below
  the baseline (e.g. minus-strand signal plotted as negative).
- Restored the UCSC backup-host's 1-hour "sticky" cache (removed in an
  earlier customization pass): once a request fails over to the backup host,
  subsequent requests go straight there for an hour instead of each one
  re-paying a fresh connect-timeout against the still-down primary host.
- Fixed `WindowFunction.absoluteMax`: bigwig zoom-pyramid records already
  carry both min and max, so it's now read directly from the pyramid (`max`
  where `|min| <= |max|`, else `min`) like every other window function,
  instead of always falling back to a separate raw-data path whose tile-range
  math didn't match the requested query and returned data from far outside
  it. This was the root cause of an "Average With Error Bar" track (whose
  members were left on the default "None" windowing, silently resolved to
  `absoluteMax`) computing wildly inflated averages.
- Added a proper "None" windowing display/export mode instead of routing it
  through `absoluteMax`: an ordinary numeric track now shows the true max
  (above baseline) and/or min (below baseline) actually present in each
  displayed bin - not a smoothed average - matching how "None" looked before
  this fork added equal-bin-count resampling. TSV export mirrors this: a
  track that's one-signed everywhere gets its normal single column: one that
  has any mixed-sign bin gets separate Pos/Neg columns, NA where a bin lacks
  either.
- Extended "Average With Error Bar" to support "None" windowing directly
  (member tracks keep it, rather than the average silently substituting
  `absoluteMax`): each member's own max/min is read straight from its zoom
  pyramid (never raw data), and bins where members disagree on sign get
  separate positive-group and negative-group N/mean/SD/SEM statistics -
  mirroring the plain-track envelope above, both on screen and in TSV export
  (`.pos.N/.average/.SD/.SEM` and `.neg.*` column groups when mixed, no
  suffix when one-signed). Fixed a real dilution bug found via this: re-binning
  multiple native (fine-resolution) entries into one wider display/export bin
  used an overlap-weighted average, which is correct for combining several
  Mean-windowed entries but wrong for Max/Min-derived ones - a bin's true
  peak was diluted toward zero by however much of the bin's width had no
  underlying data at all (bigwig files don't store zero-coverage positions,
  they simply omit them). Fixed by reporting the single native entry with the
  largest-magnitude mean as-is instead of averaging toward it.
- "None" is now a session-persisted Windowing Function value on its own for
  an Average track (dialog and context menu both already labeled it "None"
  for the UI-visible option that used to be `absoluteMax` internally); saving
  and restoring a session no longer needs the old None<->absoluteMax
  translation.

## August 11, 2026

- Added **View > Set IGV Window Size** for applying exact pixel dimensions to
  the current window when reproducing figure layouts. Save Screenshot displays
  the current dimensions and provides the same Set action; startup still follows
  IGV's normal saved-window behavior, and dimensions are not added to TSV output.
- Made Save Screenshot remember its last accepted path and options for the
  current IGV run, and clarified that the General bin setting controls both
  numeric display and TSV export.
- Completed and stabilized Regional Settings after its initial preview,
  including regional rendering, navigation, nested coordinate transforms,
  sequence direction, annotation labels, track-setting transfer, screenshot/TSV
  export, session persistence, and the remaining interaction fixes listed below.
- Restored black ROI boundary guides on hover, made ROI strips 50% transparent,
  and made overlapping regions paint large-to-small and select the smallest
  containing region first.
- Extended region-wide background and foreground colors across the actual track
  divider components, and corrected annotation-label clipping in
  coordinate-inverted regional passes.
- Refined Regional Settings with double-click editing, standard inversion
  checkboxes, row-scoped Y-axis controls, distinct Pair Swap/Pair Flip modes with
  data-range validation, source-aware positive/negative color swapping,
  reorganized actions, and region descriptions in dialog titles. Changes now
  preview live while Cancel/Esc restores the original rule; cell selection keeps
  color swatches visible and button actions preserve table focus. Region
  Navigator now opens at a compact width.
- Made each ROI bar color independently configurable and session-persistent,
  repainted newly added Navigator regions immediately, and allowed the Invert
  Coordinates column to enter editing with one click while retaining double-click
  editing for the other editable settings.
- Prevented feature-track margins from accumulating across normal and regional
  render passes, which had shifted inverted annotations downward and suppressed
  their labels. Regional Settings now keeps cell selections after toolbar actions,
  uses a black inline editing tip, and provides a resizable Track row-header that
  follows dialog width changes.
- Anchored regional coordinate inversion to the ROI's fixed genomic boundaries
  instead of its viewport-clipped rectangle, so partially visible and zoomed-in
  inverted tracks pan in the same screen direction as ordinary track content.
  Regional Settings now places Reset and confirmation actions on one row and uses
  clearer table-editing guidance.
- Deferred annotation labels to a final track label layer and selected the normal
  or region-transformed label by feature ownership, preventing text from being
  cut at regional boundaries while preserving foreground-mask coverage. Collapse
  navigation now keeps the original bp-per-pixel scale, fills from genomic data
  beyond the deleted interval, and skips collapsed coordinates during panning
  instead of dynamically squeezing the remaining viewport.
- Added one final screen-space collision pass for collected annotation labels, so
  labels originating on opposite sides of a regional boundary no longer overlap.
- Renamed the ROI sort command to **Sort SEG Track by Value**, placed it below
  BLAT, and hide it when no SEG track is loaded. Sequence and three-frame
  translation rendering now load every disjoint genomic source interval needed
  by regional coordinate inversion, including partially visible and nested ROIs.
- Made region-inverted sequence content biologically reverse-complemented and
  switched its three-frame translation to the corresponding opposite strand.
  Screenshot TSV now keeps `chr/start/end` as the common screen-bin key, adds
  compact source columns only for regionally transformed tracks, obtains pair
  values from the partner track, follows the extended post-collapse viewport,
  and records collapsed intervals in a `bin_note` metadata row.
- Standardized screenshot TSV headers to underscore-normalized track names and
  dot-separated fields. Average With Error Bar now validates regional settings
  within each output group, inherits matching overrides, and offers an explicit
  reset-and-continue path for conflicts. Empty ROI-bar space now offers Add Region
  and Region Navigator actions; Navigator chromosome cells are editable while
  regional settings are inactive and its adjustable table text renders black.
- Unified regional-setting transfer for composite numeric tracks. Average restore
  and Overlay separation now copy the composite track's current per-region settings
  back to individual members and remove the deleted composite override; Overlay
  creation also validates and inherits matching member settings. Regional Pair
  Swap/Pair Flip is intentionally omitted when individual pair relationships are
  ambiguous, with a warning only when such a pair mode was actually present.
- Repainted the main IGV view immediately after Region Navigator removes an ROI,
  preventing a stale region bar from remaining until the next mouse hover.
- Preserved a common manually configured Data Range and matching positive/negative
  track colors when creating each Average With Error Bar output group. Click and
  feature-selection coordinates inside inverted regions now use the same fixed
  ROI reflection axis as rendering, including partially visible and nested
  inverted regions.
- Added a 20-step application Undo/Redo history with an Edit menu and standard
  Ctrl/Cmd shortcuts. User-deleted tracks retain their live panel state and
  original position until restored or evicted from history; ROI additions,
  deletions, and accepted Regional Settings dialogs are also single undoable
  edits. Detached tracks are unloaded when their history entry expires.
- Extended Undo/Redo to interactive track loading; data range, autoscale,
  positive/negative/background/border color, height, renderer, and window
  function changes; Pair/Unpair and paired Y-axis flips; drag or dialog track
  reordering; Average creation/Restore; and Overlay creation/Separate. Composite
  transactions retain exact pane objects, member state, pairing, and regional
  rules. Session and genome initialization clear the bounded history because
  they establish a new non-undoable baseline.
- Clipped Average With Error Bar uncertainty markers to the mean's side of the
  data-range midline, preventing ochre error bars from protruding across the gray
  line in SVG output.

## August 10, 2026

- Added the initial preview of ROI-level display transformations. Regions can
  collapse from the display, invert coordinates for all or selected tracks,
  flip or customize regional Y ranges, and override background, foreground,
  positive, and negative colors without changing source data.
- Redesigned Region Navigator around a compact **Regional Settings** editor with
  row/column multi-selection, copy/paste, color and Y-axis controls,
  positive/negative color swapping, and cell/row/column/all reset actions. The
  same editor is available from region and in-region track context menus.
- Added boundary-aware regional binning and piecewise coordinate mapping:
  off-screen ROIs do not create bins, bins split at visible ROI boundaries,
  nested coordinate/Y inversions compose, and paired-track Y flips exchange the
  affected segment between partners. Active settings lock ROI boundaries until
  reset.
- Fixed annotation labels disappearing inside coordinate-inverted regions,
  extended transparent-border region fills through boundary pixels, and
  prevented exported SVG bars from crossing the zero-axis line. At this stage,
  Regional Settings still had known minor rendering and UI bugs that were fixed
  on August 11.
- Added automatic build identity to About IGV and terminal startup output:
  upstream/customization version, commit (including dirty state), deterministic
  `src/main/` tree SHA-256, build time, and SHA-256 of the running `igv.jar`. About
  IGV also provides a Copy Build Info button for comparing installations.
- Replaced the separate File-menu PNG/SVG commands with a publication-oriented
  Save Screenshot dialog. It supports PNG or SVG output, independent inclusion
  of the top genomic coordinates and track-name column, and an optional
  1-based inclusive coordinate suffix in output filenames.
- Added optional TSV export alongside screenshots. Numeric data are summarized
  into equal genomic bins; absent values are written as `NA`; merged tracks
  retain columns for their members; average tracks export N, average, SD, and
  SEM; and base-resolution views include a strand-labelled reference-sequence
  column.
- Added a General preference for numeric display and TSV bin count, with a
  default of 1500 equal genomic bins, and applied the same binning model to
  visible numeric tracks.
- Added an Invert genomic coordinates checkbox that reverses the displayed
  genomic axis, sequence direction indicator, feature geometry, and transcript
  arrows while keeping text and Y-axis labels upright. Inversion is also
  reflected in screenshot filenames and TSV row order and is saved in JSON
  sessions.
- Added Flip Y-Axis for numeric tracks. Paired tracks exchange top/bottom
  positions and roles as well as reversing their ranges; the operation disables
  conflicting autoscaling, persists through sessions, and repaints only the
  affected viewports to avoid occasional full-session pauses.
- Restored the overlay opacity command under the clearer Adjust Overlay
  Transparency label, saved merged-track opacity in XML/JSON sessions, placed
  Separate Tracks directly below it, and unified checked and unchecked overlay
  context menus. Renderer choices are hidden for overlay-only selections and
  apply only to ordinary numeric tracks in mixed selections.
- Made screenshot rendering preserve the configured track backgrounds, borders,
  coordinate area, and track-name area instead of relying on mismatched export
  defaults.
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
