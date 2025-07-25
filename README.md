
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

## Message to US citizen and far right supporters

If you support Trump or any other party close to the far right, I ask you not to use this software.

My values are fundamentally opposed to those of these parties, and I do not wish for my work, which I have developed during evenings and weekends, and despite it being open source, to be used by people who support these nauseating ideas.

Solidarity, openness to others, ecology, fight against discrimination and inequality, respect for all religions, genders, and sexual orientations are the values that drive me.
I do not accept that my work be used by people who are responsible for suffering and exclusion.
If you do, I kindly ask you to review your choices and turn to more positive values, where your well-being does not come from the rejection of others.

## Community

[<img src="https://discordapp.com/api/guilds/1305595962663768074/widget.png?style=banner2">](https://discord.gg/y9NCGaWzve)

## Donations

If you appreciate my work, I do not accept direct donations.
However, I strongly encourage you to make a donation to the [STAROS association](https://www.helloasso.com/associations/single-tracking-astronomical-repository-for-open-spectroscopy/formulaires/3), which initiated the Sol'Ex and Sunscan projects and promotes spectrography using innovative projects with amateurs, schools and science collaborations: that will be my contribution to this project and will be greatly appreciated!

Click on the image below to donate:

[<img src="https://staros-projects.org/assets/img/backgrounds/STAROS_logo_text.png" height="150">](https://www.helloasso.com/associations/single-tracking-astronomical-repository-for-open-spectroscopy/formulaires/3)

Of course, you can also send a message of encouragement or appreciation, it is always nice to receive them!

## Download links

The following binaries are provided as a convenience and built from sources using GitHub Actions.
They are provided as-is, without any warranty.

- Latest release is version 3.3.2 ([documentation](https://melix.github.io/astro4j/3.3.2))
  - [JSol'Ex 3.3.2 (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-ubuntu-latest/jsolex_3.3.2_amd64.deb)
  - [JSol'Ex 3.3.2 (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-windows-latest/jsolex-3.3.2.msi)
  - [JSol'Ex 3.3.2 (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-3.3.2.pkg)
  - [JSol'Ex (Zip Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-windows-latest/jsolex-3.3.2.zip)
  - [JSol'Ex (Zip Linux)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-ubuntu-latest/jsolex-3.3.2.zip)
  - [JSol'Ex (Zip MacOS ARM64)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-3.3.2.pkg)
  - [JSol'Ex (Zip MacOS Intel)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-13/jsolex-3.3.2.pkg)
  - [Ser Player 3.3.2 (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-ubuntu-latest/ser-player_3.3.2_amd64.deb)
  - [Ser Player 3.3.2 (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-windows-latest/ser-player-3.3.2.msi)
  - [Ser Player 3.3.2 (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-3.3.2.pkg)
  - [Ser Player (Zip Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-windows-latest/ser-player-3.3.2.zip)
  - [Ser Player (Zip Linux)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-ubuntu-latest/ser-player-3.3.2.zip)
  - [Ser Player (Zip MacOS ARM64)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-3.3.2.zip)
  - [Ser Player (Zip MacOS Intel)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-13/ser-player-3.3.2.zip)

You can also install the _development_ version of JSol'Ex as it can contain numerous bugfixes and improvements over the release versions.

- Development versions (updated on each commit)
  - [JSol'Ex (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-ubuntu-latest/jsolex-devel_3.4.0_amd64.deb)
  - [JSol'Ex (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-windows-latest/jsolex-devel-3.4.0.msi)
  - [JSol'Ex (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-devel-3.4.0.pkg)
  - [JSol'Ex (MacOS Intel)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-13/jsolex-devel-3.4.0.pkg)
  - [JSol'Ex (Tarball)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-3.4.0-SNAPSHOT.tar.gz)
  - [JSol'Ex (Zip Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-windows-latest/jsolex-3.4.0-SNAPSHOT.zip)
  - [JSol'Ex (Zip Linux)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-ubuntu-latest/jsolex-3.4.0-SNAPSHOT.zip)
  - [JSol'Ex (Zip MacOS ARM64)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-3.4.0-SNAPSHOT.pkg)
  - [JSol'Ex (Zip MacOS Intel)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-13/jsolex-3.4.0-SNAPSHOT.pkg)
  - [Ser Player (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-ubuntu-latest/ser-player-devel_3.4.0_amd64.deb)
  - [Ser Player (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-windows-latest/ser-player-devel-3.4.0.msi)
  - [Ser Player (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-devel-3.4.0.pkg)
  - [Ser Player (Tarball)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-3.4.0-SNAPSHOT.tar.gz)
  - [Ser Player (Zip Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-windows-latest/ser-player-3.4.0-SNAPSHOT.zip)
  - [Ser Player (Zip Linux)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-ubuntu-latest/ser-player-3.4.0-SNAPSHOT.zip)
  - [Ser Player (Zip MacOS ARM64)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-3.4.0-SNAPSHOT.zip)
  - [Ser Player (Zip MacOS Intel)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-13/ser-player-3.4.0-SNAPSHOT.zip)

Licensed under Apache License version 2.

## Building from sources

You need to have a Java 23 SDK to build from sources.
I recommend that you install [GraalVM for Java 23](https://www.graalvm.org/).

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
