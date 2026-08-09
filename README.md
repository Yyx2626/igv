# igv

![Build Status](https://github.com/igvteam/igv/actions/workflows/gradle.yml/badge.svg)
![GitHub issues](https://img.shields.io/github/issues/igvteam/igv)
![GitHub closed issues](https://img.shields.io/github/issues-closed/igvteam/igv)
![](https://img.shields.io/npm/l/igv.svg)

Integrative Genomics Viewer - desktop genome visualization tool for Mac, Windows, and Linux.

## About this fork

This repository was forked from [`igvteam/igv`](https://github.com/igvteam/igv)
on August 9, 2026. The customization work started from upstream commit
[`5f20cadf7`](https://github.com/igvteam/igv/commit/5f20cadf7fa10d405e7a8ecd9255a1ed502d9115)
(`Restore "new session" and "reload session"`), cloned on August 5, 2026. This
stable customization snapshot contains 12 local commits through
[`8e8d19d1d`](https://github.com/Yyx2626/igv/commit/8e8d19d1d).

### Downloads

**Download the compiled customized IGV builds from the
[`Yyx2626/igv` Releases page](https://github.com/Yyx2626/igv/releases).**

This is the permanent download location for this customized fork. Individual
versioned installers and archives will appear there as releases are published.
For the original, unmodified IGV project, visit
[`igvteam/igv`](https://github.com/igvteam/igv).

### Major customizations

- **Track pairing:** pair two tracks as top and bottom members, edit their data
  ranges together, autoscale paired sides independently, and persist pairing in
  JSON sessions.
- **Numbered track groups:** define, extend, and recall groups 1-9 with keyboard
  shortcuts or the group-tab controls below the track area.
- **Average with error bars:** combine selected tracks into a synthetic mean
  track with SEM, SD, or no error bars; configure missing-value handling,
  windowing, rendering, color, cap style, and restore the original tracks.
- **Appearance controls:** configure global or per-track backgrounds and divider
  colors/heights, customize data-range mid-line colors, and use visually
  zero-height dividers that remain draggable through a floating hover overlay.
- **Portable launchers:** select bundled JDK 21 runtimes by operating system and
  CPU architecture on macOS and Windows.

### Minor fixes and debugging

- Fixed duplicate loading of files dropped from macOS Finder and made divider
  drag-and-drop use the live panel position instead of stale cached state.
- Fixed negative-only autoscaling, Y-axis and mid-line clipping, viewport and
  track background gaps, and error bars being clipped by autoscaling.
- Fixed selection behavior for Shift-click, right-click, checkboxes, and mixed
  multi-track context menus.
- Fixed JSON session restoration for local merged/average tracks, pair-group
  columns, group tabs, track order, restored data ranges, and stale pair links.
- Fixed missing-value statistics and pixel alignment in average/error-bar
  rendering.
- Fixed broken `igvtools` classpath launchers and bundled-JDK selection scripts.
- Made HTTP connect/read timeouts configurable and reduced their defaults to
  avoid multi-minute startup stalls when a remote annotation host is unavailable.

The individual commits contain the implementation rationale and detailed bug
causes. This branch is a customized IGV build and is not an official
`igvteam/igv` release.

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


 
