# Welcome to JSol'Ex {{version}}!

## What's New in Version 5.0.5

- All images can now be uploaded to SpectroSolHub, not just those where the solar disk was detected
- Fixed script repositories with spaces in script filenames failing to download
- Improved error messages when loading SpectroSolHub repositories fails
- Re-running an ImageMath script after tweaking a parameter is now much faster: only expressions that depend on the changed parameter are recomputed.
- Added two ImageMath functions, `scale_to_unit` and `scale_from_unit`, to convert pixel values between the [0;65535] and [0;1] ranges (with optional clamping).
- Improved `dedistort` quality on multi-iteration consensus runs via adaptive per-tile convergence.

## What's New in Version 5.0.4

- Fixed a performance issue in Helium extraction when GPU acceleration was disabled
- Fixed a thread starvation issue that could lead to incomplete processing
- Added auto-alignment in BASS2000 submission wizard

## What's New in Version 5.0.3

- Added a new "Sharing" menu with items for publishing to BASS2000 and sharing on SpectroSolHub
- Added the ability to post-process images externally before uploading to SpectroSolHub. In the image selection step, check the post-processing option to edit your images (contrast, sharpening, etc.) in your favorite editor before upload.
- Fixed non deterministic dedistorsion results
- Improved performance on multi-core systems
- Reworked Helium line image extraction
- Added the ability to declare the type of image generated in scripts

## What's New in Version 5.0.2

- Added the ability to configure the level of parallelism used in batch processing mode
- Fixed GPU acceleration failing on devices with limited work group sizes, causing unnecessary fallback to CPU
- When a previous OpenGL crash is detected, a dialog now offers the option to retry instead of silently disabling the 3D viewer

## What's New in Version 5.0.1

- Fixed SpectroSolHub image upload not applying transformations (such as P angle correction) to images that were not individually viewed before uploading
- Fixed SpectroSolHub orientation comparison step not applying P angle correction to the user image when comparing with the GONG reference

## What's New in Version 5.0.0

- [SpectroSolHub Integration](#spectrosolhub-integration)
- [Python Scripting](#python-scripting)
- [Doppler Rotation Correction](#doppler-rotation-correction)
- [Spectrum Browser Improvements](#spectrum-browser-improvements)
- [New ImageMath Functions](#new-imagemath-functions)
- [Bug Fixes and Improvements](#bug-fixes-and-improvements)

## SpectroSolHub Integration

[SpectroSolHub](https://spectrosolhub.com) is a newly released companion to JSol'Ex, currently in beta. It is a service which lets you share spectroheliographic images with the community. It is written by the author of JSol'Ex.

![SpectroSolHub](/docs/spectrosolhub.png)

You can now publish your processed images directly to SpectroSolHub from JSol'Ex. After processing a SER file, click the "SpectroSolHub" button in the status bar to open the publishing wizard. The wizard guides you through authentication, image selection, session metadata, and upload.

You can also browse and add script repositories from SpectroSolHub directly from the script repositories manager. Click "Browse SpectroSolHub" to discover available script repositories and add them with a single click.

**Note:** By uploading images to SpectroSolHub, you grant a non-exclusive, worldwide, royalty-free license to use your uploaded content for scientific research purposes.

## Python Scripting

JSol'Ex 5.0 introduces Python scripting, a major step forward in extensibility.
You can now write image processing scripts in Python, in addition to the existing ImageMath language.
Python scripts have access to the full processing pipeline and can leverage the rich Python ecosystem for advanced analysis.

Refer to the documentation for details on how to write and use Python scripts.

## Doppler Rotation Correction

JSol'Ex 5.0 introduces a new "Doppler (rotation corrected)" image type.
This generates a Doppler image with the smooth solar rotation gradient removed using 2D polynomial fitting, making chromospheric velocity features much easier to see.

Two new ImageMath functions support custom Doppler correction workflows: `SIGNED_DIFF(a, b)` computes the difference between two images preserving sign (without normalization), while `POLY_FIT_2D(image, degree)` fits a 2D polynomial surface within the solar disk.

## Spectrum Browser Improvements

The spectrum browser has been significantly improved in this release:

- Wavelength grid labels now auto-adjust to the zoom level, with minor tick marks and faint grid lines for easier reading
- New zoom level presets (25%, 50%, 75%, 100%, 150%, 200%, 400%) relative to instrument dispersion

## New ImageMath Functions

- `COLLAGE`: create image layouts using a text pattern (e.g., `collage(".X. / X.X", images)` for a pyramid layout)
- `SIGNED_DIFF(a, b)`: computes the difference between two images preserving sign
- `POLY_FIT_2D(image, degree)`: fits a 2D polynomial surface within the solar disk

## Bug Fixes and Improvements

- Fixed a memory leak that would be visible during reconstruction with very large SER files

## Message to US citizen and far right supporters

**If you support Trump or any other party close to the far right, I ask you not to use this software.**

My values are fundamentally opposed to those of these parties, and I do not wish for my work, which I have developed during evenings and weekends, and despite it being open source, to be used by people who support nauseating ideas.

Solidarity, openness to others, ecology, fight against discrimination and inequality, respect for all religions, genders, and sexual orientations are the values that drive me.
I do not accept that my work be used by people who are responsible for suffering and exclusion.
If you do, I kindly ask you to review your choices and turn to more positive values, where your well-being does not come from the rejection of others.
