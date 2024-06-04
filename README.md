
# Astro4j

A collection of libraries and applications for astronomy image processing in Java.

The project currently only consists of:

- [jserfile](jserfile/) : a library to read SER video files (for developers)
- [math](math/) : a library with maths utilities like linear and ellipse regression, image convolution, etc. (for developers)
- [ser-player](ser-player/) : an application which plays SER files
- [JSol'Ex](jsolex) : an application to process [Sol'Ex](http://www.astrosurf.com/solex/) video files
- [JSol'Ex CLI](jsolex-cli) : a command-line application to process [Sol'Ex](http://www.astrosurf.com/solex/) video files. This version has to be built from sources, I do not provide installers yet.

If you want some context about why I started developing this, you can read my [announcement blog post](https://melix.github.io/blog/2023/04-22-introducing-astro4j.html).

## Download links

The following binaries are provided as a convenience and built from sources using GitHub Actions.
They are provided as-is, without any warranty.

- Latest release is version 2.4.1 ([documentation](https://melix.github.io/astro4j/2.4.1))
  - [JSol'Ex 2.4.1 (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-ubuntu-latest/jsolex_2.4.1_amd64.deb)
  - [JSol'Ex 2.4.1 (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-windows-latest/jsolex-2.4.1.msi)
  - [JSol'Ex 2.4.1 (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-2.4.1.pkg)
  - [JSol'Ex (Tarball)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-2.4.1.tar.gz)
  - [JSol'Ex (Zip)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-2.4.1.zip)
  - [Ser Player 2.4.1 (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-ubuntu-latest/ser-player_2.4.1_amd64.deb)
  - [Ser Player 2.4.1 (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-windows-latest/ser-player-2.4.1.msi)
  - [Ser Player 2.4.1 (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-2.4.1.pkg)
  - [Ser Player (Tarball)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-2.4.1.tar.gz)
  - [Ser Player (Zip)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-2.4.1.zip)

You can also install the _development_ version of JSol'Ex as it can contain numerous bugfixes and improvements over the release versions.

- Development versions (updated on each commit)
  - [JSol'Ex (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-ubuntu-latest/jsolex-devel_2.4.2_amd64.deb)
  - [JSol'Ex (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-windows-latest/jsolex-devel-2.4.2.msi)
  - [JSol'Ex (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-devel-2.4.2.pkg)
  - [JSol'Ex (Tarball)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-2.4.2-SNAPSHOT.tar.gz)
  - [JSol'Ex (Zip)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-2.4.2-SNAPSHOT.zip)
  - [Ser Player (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-ubuntu-latest/ser-player-devel_2.4.2_amd64.deb)
  - [Ser Player (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-windows-latest/ser-player-devel-2.4.2.msi)
  - [Ser Player (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-devel-2.4.2.pkg)
  - [Ser Player (Tarball)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-2.4.2-SNAPSHOT.tar.gz)
  - [Ser Player (Zip)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-2.4.2-SNAPSHOT.zip)

Licensed under Apache License version 2.

## Building from sources

You need to have a Java 21 SDK to build from sources.
I recommend that you install [GraalVM for Java 21](https://www.graalvm.org/).

This project makes use of [Gradle](https://gradle.org) to build.
Here are some common tasks you may want to execute:

| Description                 |Task|Example|
|-----------------------------|----|-------|
| Run the application         |`run`|`./gradlew :jsolex:run`|
| Build without running tests |`assemble`|`./gradlew :ser-player:assemble|
| Package as a Zip file       |`jlinkZipArchive`|`./gradlew :jsolex:jlinkZipArchive` <br/>the look into the `jsolex/build/installers` directory|
| Execute tests               |`test`|`./gradlew :ser-player:test`|
| Build all distributionss    |`allDistributions`|`./gradlew allDistributions` <br/>then look into the `build/installers` directories|

Tasks can be executed on all projects by running `./gradlew <task>` or on a single subproject by running `./gradlew :subproject:<task>`.

The most common projects you'll want to run on are `jsolex` (the JSol'Ex application) or `ser-player` (the SER Player application).
For example : `./gradlew :jsolex:run`.
