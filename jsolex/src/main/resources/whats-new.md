# Welcome to JSol'Ex {{version}}!

- [What's New in Version 4.2.1](#whats-new-in-version-4-2-1) - Bug fixes
- [Version 4.2.0](#whats-new-in-version-4-2-0) - Script repositories, Java 25, GIF support, file format settings
- [Version 4.1.4](#whats-new-in-version-4-1-4) - Bug fixes and improvements
- [Version 4.1.3](#whats-new-in-version-4-1-3) - Improved banding correction
- [Version 4.1.2](#whats-new-in-version-4-1-2) - Bug fixes
- [Version 4.1.1](#whats-new-in-version-4-1-1) - Bug fixes
- [Version 4.1.0](#whats-new-in-version-4-1-0) - User-defined presets, collage creation
- [Version 4.0.1](#whats-new-in-version-4-0-1) - Language selection, bug fixes
- [Version 4.0.0](#whats-new-in-version-4-0-0) - Enhanced UI, BASS2000 integration

## What's New in Version 4.2.1

- Fixed globe not positionned correctly in measure tool when autocrop was not used
- Fixed SER frames extraction tool only exporting MP4 files even when both MP4 and GIF were selected in advanced parameters
- Improve memory management so that processing works on lower memory systems

## What's New in Version 4.2.0

- Upgraded to Java 25
- File formats are now part of advanced settings (outside of process parameters)
- Added ability to generate GIF files in addition to MP4
- Add support for declaring [script repositories](#script-repositories)

## What's New in Version 4.1.4

- Fixed memory leak
- Clarify display of conversion from pixels to Angstroms in the process parameters dialog
- Fix `COLORIZE` not accepting a wavelength parameter as advertised
- Fix edge case where colorization could fail
- Fix pixel shift display in observation details
- Fixed excessive banding correction

## What's New in Version 4.1.3

- Improved banding correction
- Improved stacking/dedistortion algorithm
- Added `STACK_DEDIS` function to stack using distortion as weight

## What's New in Version 4.1.2

- Added file name field in collage creation dialog to allow custom naming of collage files
- Fixed orientation of wing images not being applied (BASS2000)
- Reduced memory usage when generating collages

## What's New in Version 4.1.1

- Fixed collage background color selection not properly applied
- Fixed collage padding parameter not properly controlling spacing between images
- Fixed scripts which fail if they contained a `[params]` block

## What's New in Version 4.1.0

- **User-Defined Presets**: Create, save, and manage your own custom image selection and script presets alongside the existing Quick and Full mode presets
- Scripts can now declare their parameters which will automatically be configurable in the user interface
- Added blend mode for BASS2000 image alignment with adjustable opacity
- Added a warning when submitting to BASS2000 a file which was already submitted on the same day for the same wavelength
- Fixed BASS2000 upload button being enabled before files are fully saved to disk
- [Image Collage Creation](#image-collage-creation): New collage feature allows combining multiple processed images into a single composite image with customizable layout and spacing

## What's New in Version 4.0.1

- [ui] Let the user select language of the application in the preferences
- [bugfix] Fixed intrusive completion popups
- [bugfix] Fixed INSTRUME field in BASS2000 submission

## What's New in Version 4.0.0

- [Enhanced User Interface](#enhanced-user-interface)
- [BASS2000 Integration](#bass2000-integration)
- [Manual Ellipse Fitting](#manual-ellipse-fitting)
- [Automatic Scripts](#automatic-scripts)
- [New ImageMath Functions](#new-imagemath-functions)
- [Bug Fixes and Improvements](#bug-fixes-and-improvements)

## Enhanced User Interface

JSol'Ex 4.0 features a completely redesigned process parameters interface.  
The interface has been modernized with improved styling and better organization of controls.  
Some features have been simplified to make the interface more intuitive.

![Nouvelle interface utilisateur](/docs/new-ui-en.png)

## BASS2000 Integration

This version introduces the long awaited integration with the BASS2000 solar database.
This will let you submit your observations to a professional database and contribute to solar research.
You will need to complete a short registration process to get approved as a contributor.
A submission wizard guides you through the process of uploading your observations to contribute to scientific research.

![BASS2000 Submission Wizard](/docs/bass2000-en.png)

BASS2000 accepts observations from approved instruments (Sol'Ex variants and MLAstro SHG 700) that meet specific requirements:
- Full solar disk images only (no partial disks, stacks or mosaics)
- Images must be from single scans without additional transforms or contrast enhancement
- Angle P correction must be applied with tilt below 1°
- Mount must be properly polar aligned
- Supported wavelengths: H-alpha, H-alpha blue continuum, CaII K, CaII blue wing, CaII H center

The wizard automatically validates your processed images against these requirements and handles the submission process.

## Manual Ellipse Fitting

JSol'Ex 4.0 introduces manual ellipse fitting for cases where automatic solar disk detection fails.  
When the automatic ellipse detection cannot properly identify the solar disk boundaries, you can now manually draw an ellipse around the solar disk to ensure accurate geometry correction.

![Manual Ellipse Fitting](/docs/assisted-fit-en.png)

This feature is particularly useful for:
- Images with poor contrast or unusual lighting conditions
- Partial solar disk observations
- Cases where automatic detection is confused by artifacts or prominences
- Fine-tuning the geometry correction for optimal results

To use manual ellipse fitting, select "User assisted" in the ellipse fitting mode dropdown in the Advanced Process Parameters section.

## Automatic Scripts

JSol'Ex 4.0 introduces the ability to run scripts automatically for specific wavelengths.  
This feature allows you to set up standardized processing workflows that execute consistently across observations.

The script execution has been improved to ensure batch sections work properly in both single file and batch processing modes.  
This fixes previous issues where batch scripts might not execute as expected.

## New ImageMath Functions

Two new statistical functions have been added to the ImageMath module:

- `avg2`: computes the average of multiple images with sigma clipping to automatically reject outliers
- `median2`: computes the median of multiple images with configurable sigma-based outlier detection

These functions are particularly useful when combining multiple observations, as they help reduce noise and eliminate artifacts that might appear in individual frames.

## Image Collage Creation

JSol'Ex 4.1 introduces a new feature to create image collages from multiple processed images.
This allows you to combine several images into a single composite image with customizable layout and spacing.

![Image Collage Creation](/docs/collage-interface-fr.jpg)

You can select multiple processed images and arrange them in a grid.

## Script Repositories

JSol'Ex 4.2 introduces support for script repositories, allowing users to discover and automatically download ImageMath scripts published by the community.
Users can declare repositories via the Tools menu by providing a name and URL.
Script repositories are a convenient way to extend the capabilities of JSol'Ex with community-contributed scripts.

## Bug Fixes and Improvements

This version fixes several bugs and includes various improvements:

- Fixed a bug where batch sections of automated scripts were not executed in batch mode
- Fixed reading of legacy FITS files written by previous versions of JSol'Ex
- Fixed false positives in Ellerman Bomb detection occurring near the solar limb
- Fixed incorrect color scheme being applied when autodetect mode is enabled
- Fixed errors not being properly propagated in batch processing mode
- Fixed ESC key still starting the processing instead of canceling
- Fixed missing tooltips and form validation in various dialog boxes
- Images generated using scripts are no longer automatically stretched by default (fixes issue #660)
- Added the ability to add more files to an existing batch for processing
- Added option to download GONG images at arbitrary date and time
- Added fixed size option to the autocrop menu to maintain specific image dimensions
- The run button is now disabled during processing to prevent accidental multiple starts

## Message to US citizen and far right supporters

**If you support Trump or any other party close to the far right, I ask you not to use this software.**

My values are fundamentally opposed to those of these parties, and I do not wish for my work, which I have developed during evenings and weekends, and despite it being open source, to be used by people who support nauseating ideas.

Solidarity, openness to others, ecology, fight against discrimination and inequality, respect for all religions, genders, and sexual orientations are the values that drive me.
I do not accept that my work be used by people who are responsible for suffering and exclusion.
If you do, I kindly ask you to review your choices and turn to more positive values, where your well-being does not come from the rejection of others.
