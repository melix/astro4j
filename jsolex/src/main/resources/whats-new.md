# Welcome to JSol'Ex {{version}}!

- [What's New in Version 4.4.3](#whats-new-in-version-4-4-3) - UI improvements, new functions, BASS2000 validation
- [Version 4.4.2](#whats-new-in-version-4-4-2) - Educational explanations
- [Version 4.4.1](#whats-new-in-version-4-4-1) - Bug fixes, improvements
- [Version 4.4.0](#whats-new-in-version-4-4-0) - 3D Spectral Profile viewer, Spherical Tomography volume renderer
- [Version 4.3.1](#whats-new-in-version-4-3-1) - New stretching functions, experimental GPU acceleration
- [Version 4.3.0](#whats-new-in-version-4-3-0) - Faster script execution, improved stacking and bug fixes
- [Version 4.2.1](#whats-new-in-version-4-2-1) - Bug fixes
- [Version 4.2.0](#whats-new-in-version-4-2-0) - Script repositories, Java 25, GIF support, file format settings
- [Version 4.1.4](#whats-new-in-version-4-1-4) - Bug fixes and improvements
- [Version 4.1.3](#whats-new-in-version-4-1-3) - Improved banding correction
- [Version 4.1.2](#whats-new-in-version-4-1-2) - Bug fixes
- [Version 4.1.1](#whats-new-in-version-4-1-1) - Bug fixes
- [Version 4.1.0](#whats-new-in-version-4-1-0) - User-defined presets, collage creation
- [Version 4.0.1](#whats-new-in-version-4-0-1) - Language selection, bug fixes
- [Version 4.0.0](#whats-new-in-version-4-0-0) - Enhanced UI, BASS2000 integration

## What's New in Version 4.4.3

- 3D viewers now offer multiple animation patterns: click the pattern preview icon to cycle through options including a simple left-right movement
- Added `SIDE_BY_SIDE` function: combines two images horizontally (left and right)
- Added `TOP_BOTTOM` function: combines two images vertically (top and bottom)
- Made `CONSENSUS` mode the default for stacking and added a sparse mode option, which makes stacking faster
- Added a database of equipment to correct metadata entry errors (pixel size, BASS2000, etc.)
- Reworked progress handling to be more responsive and informative during long operations
- Fixed statistics bar which could show incorrect center wavelength in some cases

## What's New in Version 4.4.2

- Added educational explanations for various features and concepts in the application. Access them via the "Help" icon which shows up on images or viewers

## What's New in Version 4.4.1

- Fixed Doppler image not being generated when the colorization curve is unchecked on H-alpha line
- Improved performance of dedistortion
- Support RGB images in ellipse fitting
- Added ability to load images from the "Tools" menu
- Fixed 3D viewer not using the stretched image
- Improved graphics card compatibility detection for the spherical tomography viewer: automatically falls back to layers view on unsupported hardware
- Fixed UI blocking on some operations (tomography, custom animations) when manual ellipse fitting mode is selected

## What's New in Version 4.4.0

- Profile chart legend is now interactive: click on legend labels to show or hide individual data series
- New 3D Spectral Line Profile viewer: visualizes spectral line intensity as a 3D surface across slit positions and wavelength offsets. Access it via the "3D Spectral Profile" button in the profile tab
- New Spectral Evolution viewer: shows how the spectral line profile varies along the scan direction (frame by frame at disk center)
- Spherical Tomography viewer now features a volume renderer with smooth layer blending that reveals chromospheric structures at different altitudes
- New single image 3D view: display any processed image as a 3D hemisphere. Access it via the "3D" button in the image viewer toolbar

## What's New in Version 4.3.1

- Added `PERCENTILE_STRETCH` function: stretches histogram by mapping specified percentiles to black and white points
- Added `SIGMOID_STRETCH` function: applies a sigmoid (S-curve) transformation for smooth contrast enhancement
- [Experimental GPU Acceleration](#experimental-gpu-acceleration): significantly faster image processing on compatible graphics cards
- Improved zoom behavior: zooming now centers on the mouse cursor position instead of the top-left corner, keeping the feature of interest centered in the view
- Added `consensus` reference selection mode in `STACK_REF`: uses averaged displacement fields to estimate true undistorted geometry, improving dedistortion accuracy when no single frame is optimal

### Experimental GPU Acceleration

JSol'Ex 4.3.1 introduces experimental GPU acceleration to speed up image processing.
When enabled, operations like dedistortion and stacking can be significantly faster, especially on large images.

**How to Enable**

1. Go to **Advanced Parameters** in the main window
2. Check the **GPU acceleration (Experimental)** option
3. Restart JSol'Ex

**Requirements**

- A graphics card with up-to-date drivers (NVIDIA, AMD, or Intel)
- If GPU acceleration doesn't work after enabling, try updating your graphics drivers

## What's New in Version 4.3.0

- Faster script execution by parallelizing independent expressions
- Fixed histogram in stats tab not updating when clicking on image links
- Added new tokens to the `DRAW_TEXT` function
- Added `FIT_CANVAS` function: adjusts canvas size of images to have identical dimensions without rescaling the solar disk (unlike `radius_rescale`), useful for stacking images without introducing distortion
- Improved `DEDISTORT` algorithm with hierarchical refinement for improved stability. Added an `iterations` optional parameter for finer control
- Fixed bug preventing the exposure calculator to open in some cases
- Added conditional functions `IFEQ`, `IFNEQ`, `IFGT`, `IFGTE`, `IFLT`, `IFLTE` for conditional value selection based on comparisons
- Added per-image statistics functions `IMG_AVG`, `IMG_AVG2`, `IMG_MEDIAN`, `IMG_MEDIAN2`, `IMG_MIN`, `IMG_MAX` that compute statistics across all pixels of each image (unlike `AVG`, `MEDIAN`, etc. which compute pixel-by-pixel statistics across multiple images)
- Added output metadata support in scripts: scripts can now define titles and descriptions for their outputs in the `meta` block using an `outputs` section. These are displayed in the image viewer instead of variable names
- Improved antialiasing in eclipse, mixed image, `DISK_FILL` and `DISK_MASK` functions

## What's New in Version 4.2.1

- Fixed globe not positionned correctly in measure tool when autocrop was not used
- Fixed SER frames extraction tool only exporting MP4 files even when both MP4 and GIF were selected in advanced parameters
- Improve memory management so that processing works on lower memory systems
- Add a way to set script parameters in the script dialog on the main window
- Fixed custom animation creation when P angle correction was applied
- Fixed computation of the L0 solar parameter

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
