# igv

![Build Status](https://github.com/igvteam/igv/actions/workflows/gradle.yml/badge.svg)
![GitHub issues](https://img.shields.io/github/issues/igvteam/igv)
![GitHub closed issues](https://img.shields.io/github/issues-closed/igvteam/igv)
![](https://img.shields.io/npm/l/igv.svg)

Integrative Genomics Viewer - desktop genome visualization tool for Mac, Windows, and Linux.

## About this fork

This repository was forked from [`igvteam/igv`](https://github.com/igvteam/igv)
on August 9, 2026, after customization work began from a clone of the upstream
repository on August 5, 2026. The customized source is maintained on this
repository's `main` branch; Git history records the exact upstream and local
revisions.

### Downloads

**Download the compiled customized IGV builds from the
[`Yyx2626/igv` Releases page](https://github.com/Yyx2626/igv/releases).**

This is the permanent download location for this customized fork. Individual
versioned installers and archives will appear there as releases are published.
For the original, unmodified IGV project, visit
[`igvteam/igv`](https://github.com/igvteam/igv).

### Major customizations

- **Average with error bars:** combine selected tracks into a synthetic mean
  track with SEM, SD, or no error bars; configure missing-value handling,
  windowing, rendering, color, cap style, and restore the original tracks.
- **Track pairing and track groups:** pair top and bottom tracks for coordinated
  range editing, independent autoscaling, and paired Y-axis flips; organize and
  recall numbered track groups 1-9 with shortcuts or group-tab controls.
- **Invert genomic coordinates:** reverse the horizontal genomic axis while
  keeping text and Y-axis labels upright; feature geometry, sequence direction,
  screenshots, TSV output, and JSON sessions follow the inverted view.
- **Regional settings:** attach display-only rules to regions of interest to
  collapse intervals, invert coordinates for selected tracks,
  customize regional Y ranges and colors, and support nested transformations.
- **Customizable appearance:** control track backgrounds, borders, dividers,
  data-range mid-lines, and related global or per-track styling to prepare
  consistent publication-quality figures.
- **Publication screenshot and data export:** export PNG or SVG figures with
  optional genomic coordinates and track names, plus matching binned TSV data
  that preserves merged, average, and base-resolution sequence information.
  Set the numeric-display and TSV bin count in Preferences > General (1500 by
  default), reproduce viewport dimensions with View > Set IGV Window Size, and
  reuse the last accepted screenshot path and options during the current run.
- **Undo and redo:** recover the 20 most recent track, pairing, composite-track,
  ROI, and Regional Settings edits from the Edit menu or standard shortcuts;
  session or genome changes start a fresh history.

### Minor fixes and debugging

- Made About IGV and terminal startup identify the exact customized build with
  version, commit, `src/main/` SHA-256, build time, and running-JAR SHA-256.
- Fixed duplicate loading of files dropped from macOS Finder and made divider
  drag-and-drop use the live panel position instead of stale cached state.
- Fixed negative-only autoscaling, Y-axis and mid-line clipping, viewport and
  track background gaps, and error bars being clipped by autoscaling.
- Fixed selection behavior for Shift-click, right-click, checkboxes, and mixed
  multi-track context menus.
- Fixed JSON session restoration for local merged/average tracks, pair-group
  columns, group tabs, track order, restored data ranges, and stale pair links.
- Improved the overlay workflow with consistent checked/unchecked context
  menus, adjustable and session-persistent transparency, reversible separation,
  and controls limited to settings that affect merged rendering.
- Fixed missing-value statistics, pixel alignment, and SVG mid-line clipping in
  average/error-bar rendering, and kept paired top/bottom averages adjacent at
  the original selected-track position.
- Fixed genome-switch races, added a stoppable genome-loading dialog that stays
  visible through default RefSeq loading, cached remote RefSeq annotations for
  later offline use, and kept progressively loaded tracks in file-load order
  without moving RefSeq.
- Fixed offline startup errors that could block the main window from opening by
  removing hosted-genome, OAuth, and track-hub network discovery from window
  construction; local FASTA loading now stays offline, startup windows restore
  to a usable on-screen size, and Stop is treated as normal cancellation.
- Added portable macOS and Windows launchers that select bundled JDK 21 by
  operating system and CPU architecture, and fixed the `igvtools` classpath
  launchers.
- Made HTTP connect/read timeouts configurable to avoid multi-minute stalls when
  a remote annotation host is unavailable.

The individual commits contain the implementation rationale and detailed bug
causes. This branch is a customized IGV build and is not an official
`igvteam/igv` release.

See [CHANGELOG.md](CHANGELOG.md) for a day-by-day record of customization and
debugging work.

### Building

These instructions are meant for developers interested in working on the IGV
code. For pre-built packages of this customized version, use the Releases link
above.

Builds are executed from the IGV project directory. Files will be created in the 'build' subdirectory.

IGV requires **Java 21** to build and run. Later versions of Java should work but we build and test on **Java 21**.

NOTE: If on a Windows platform use ```./gradlew.bat``` in the instructions below

#### Folder structure and build targets

The IGV bundles ship with embedded JREs from AdoptOpenJDK.

* Install Gradle for your platform. See https://gradle.org/ for details.

* Use ```./gradlew createDist``` to build a distribution directory (found in ```build/IGV-dist```) containing
  the igv.jar and its required runtime third-party dependencies as well as helper scripts for launching.

    * Launch IGV with `igv.sh` or `igv_hidpi.sh` on Linux, `igv.command` on Mac, and `igv.bat` on Windows.

    * To run igvtools from the command line use the script `igvtools` on Linux and Mac, or igvtools.bat
      on Windows. See the instructions in igvtools_readme.txt in that directory.

    * The launcher scripts expect this folder structure in order to run IGV.

* Use ```./gradlew test``` to run the test suite. See 'src/test/README.txt' for more information about running
  the tests.

* See this [README](https://raw.githubusercontent.com/igvteam/igv/master/scripts/readme.txt) for tips about using the
  IGV launcher scripts.

* This dashboard describes [project structure and dependencies](https://sourcespy.com/github/igvteamigv/).

Note that Gradle creates a number of other subdirectories in 'build'. These can be safely ignored.

#### Amazon Web Services support

Public data files hosted in Amazon S3 buckets can be loaded into IGV
using [https endpoints](https://docs.aws.amazon.com/AmazonS3/latest/dev/UsingBucket.html).

Authenticated access using s3:// urls is supported by either (1) enabling OAuth access with Cognito using the UMCCR
contributed AWS configuration option, or (2) setting AWS credentials and region information as described
[here]( https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html) and
[here](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-region-selection.html).

For more details on using Cognito for OAuth access, see
the [UMCCR documentation on the backend](https://umccr.org/blog/igv-amazon-backend-setup/)
and [frontend for a provisioning URL step by step guide](https://umccr.org/blog/igv-amazon-frontend-setup/).


 
