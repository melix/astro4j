
# Astro4j

A collection of libraries and applications for astronomy image processing in Java.

The project currently only consists of:

- [jserfile](jserfile/) : a library to read SER video files (for developers)
- [math](math/) : a library with maths utilities like linear and ellipse regression, image convolution, etc. (for developers)
- [ser-player](ser-player/) : an application which plays SER files
- [JSol'Ex](jsolex) : an application to process [Sol'Ex](http://www.astrosurf.com/solex/) video files
- [JSol'Ex CLI](jsolex-cli) : a command-line application to process [Sol'Ex](http://www.astrosurf.com/solex/) video files. This version has to be built from sources, I do not provide installers yet.

If you want some context about why I started developing this, you can read my [announcement blog post](https://melix.github.io/blog/2023/04-22-introducing-astro4j.html).

## Note aux utilisateurs français

Si vous votez Rassemblement National ou tout autre parti proche de l'extrême droite, je vous demande de ne pas utiliser ce logiciel.
Mes convictions sont diamètralement opposées à celles de ces partis et je ne souhaite pas que mon travail, développé sur mon temps libre avec une licence libre serve à des personnes qui soutiennent ces idées nauséabondes.

La solidarité, le partage, l'ouverture aux autres, la lutte contre les discriminations et les inégalités, le respect de toutes les religions, de tous les genres sont les valeurs qui m'animent. 
Elles sont à l'opposé de celles prônées par ces partis.

Je vous invite à ne pas céder aux sirènes de la haine et à vous tourner vers des valeurs plus positives, où votre bien-être ne passe pas par le rejet de l'autre.

## Download links

The following binaries are provided as a convenience and built from sources using GitHub Actions.
They are provided as-is, without any warranty.

- Latest release is version 2.7.0 ([documentation](https://melix.github.io/astro4j/2.7.0))
  - [JSol'Ex 2.7.0 (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-ubuntu-latest/jsolex_2.7.0_amd64.deb)
  - [JSol'Ex 2.7.0 (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-windows-latest/jsolex-2.7.0.msi)
  - [JSol'Ex 2.7.0 (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-2.7.0.pkg)
  - [JSol'Ex (Tarball)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-2.7.0.tar.gz)
  - [JSol'Ex (Zip)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-2.7.0.zip)
  - [Ser Player 2.7.0 (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-ubuntu-latest/ser-player_2.7.0_amd64.deb)
  - [Ser Player 2.7.0 (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-windows-latest/ser-player-2.7.0.msi)
  - [Ser Player 2.7.0 (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-2.7.0.pkg)
  - [Ser Player (Tarball)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-2.7.0.tar.gz)
  - [Ser Player (Zip)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-2.7.0.zip)

You can also install the _development_ version of JSol'Ex as it can contain numerous bugfixes and improvements over the release versions.

- Development versions (updated on each commit)
  - [JSol'Ex (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-ubuntu-latest/jsolex-devel_2.7.1_amd64.deb)
  - [JSol'Ex (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-windows-latest/jsolex-devel-2.7.1.msi)
  - [JSol'Ex (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-devel-2.7.1.pkg)
  - [JSol'Ex (MacOS Intel)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-13/jsolex-devel-2.7.1.pkg)
  - [JSol'Ex (Tarball)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-2.7.1-SNAPSHOT.tar.gz)
  - [JSol'Ex (Zip)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-2.7.1-SNAPSHOT.zip)
  - [Ser Player (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-ubuntu-latest/ser-player-devel_2.7.1_amd64.deb)
  - [Ser Player (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-windows-latest/ser-player-devel-2.7.1.msi)
  - [Ser Player (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-devel-2.7.1.pkg)
  - [Ser Player (Tarball)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-2.7.1-SNAPSHOT.tar.gz)
  - [Ser Player (Zip)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-2.7.1-SNAPSHOT.zip)

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
