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

- Latest release is version 1.1.1
  - [JSol'Ex 1.1.1 (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-ubuntu-latest/jsolex_1.1.1-1_amd64.deb)
  - [JSol'Ex 1.1.1 (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-windows-latest/jsolex-1.1.1.msi)
  - [JSol'Ex 1.1.1 (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-1.1.1.pkg)
  - [Ser Player 1.1.1 (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-ubuntu-latest/ser-player_1.1.1-1_amd64.deb)
  - [Ser Player 1.1.1 (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-windows-latest/ser-player-1.1.1.msi)
  - [Ser Player 1.1.1 (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-1.1.1.pkg)

You can also install the _development_ version of JSol'Ex as it can contain numerous bugfixes and improvements over the release versions.

- Development versions (updated on each commit)
  - [JSol'Ex (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-ubuntu-latest/jsolex-devel_1.2.0-1_amd64.deb)
  - [JSol'Ex (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-windows-latest/jsolex-devel-1.2.0.msi)
  - [JSol'Ex (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/jsolex-macos-latest/jsolex-devel-1.2.0.pkg)
  - [Ser Player (Linux, deb, AMD64)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-ubuntu-latest/ser-player-devel_1.2.0-1_amd64.deb)
  - [Ser Player (Windows)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-windows-latest/ser-player-devel-1.2.0.msi)
  - [Ser Player (MacOS)](https://jsolex.s3.eu-west-3.amazonaws.com/ser-player-macos-latest/ser-player-devel-1.2.0.pkg)


Licensed under Apache License version 2.
