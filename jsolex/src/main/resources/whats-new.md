# Welcome to JSol'Ex {{version}}!

## What's New in Version 3

- [Changes since 3.0.0](#changes-since-3.0.0)
- [Image Improvements](#image-improvements)
- [New Image Stretching Modes](#new-image-stretching-modes)
- [Physical Flat Correction](#physical-flat-correction)
- [Distance Measurement](#distance-measurement)
- [ImageMath Improvements](#imagemath-improvements)
- [Bug Fixes](#bug-fixes)

## Changes since 3.0.0

### 3.4.0

- Remove some false positives in Ellerman Bomb detection on solar limb
- Added mount, ERF and stop in observation details
- Add an "assisted ellipse fitting" mode, which allows you to draw an ellipse around the disk, when detection fails
- Moved advanced process parameters to their own section in the process settings
- Images generated using scripts are no longer stretched by default (fixes issue 660)

### 3.3.2

- Fixed detection of bit depth in SER files
- Fixed `saturate` function not working on non-square images
- Improved corona E extraction script
- Added `radius_rescale2` function which allows specifying both a target radius for the solar disk AND a fixed image width/height, which is useful for stacking externally
- Improved banding correction to reduce "polar banding" artifacts

### 3.3.1

- Fixed bug in cropping or SER frames extraction

### 3.3.0

- Fix bug in auto contrast which could cause an array index out of bounds exception
- Added `mtf` function inspired by SIRIL, which applies a Midtone Transfer Function (MTF) with configurable shadows, midtones and highlights parameters (8-bit values 0-255)
- Added `mtf_autostretch` function inspired by SIRIL, which applies automatic MTF stretching based on histogram analysis using sigma-based shadow clipping and target background level
- Make `angleP`, `b0`, ... parameters available in the `[[batch]]` section of scripts. A average value is computed from the images in the batch, and can be used in the script for convenience.
- Made it possible to select an unsharp mask as the sharpness method in the image enhancement settings
- Add corona E extraction sample script
- Display pixel shift in the technical card
- Display prominence scales in measurement window
- Add menu item to extract frames which correspond to a rectangle user selection

### 3.2.2

- Added an `unsharp_mask` function, which applies an unsharp mask to the image, enhancing details
- Optimize performance in full processing mode
- Fixed potential null pointer exception at startup
- Optimize UI responsiveness
- Enhanced profile view: clicking on reconstruction now displays both frame average and spectral line intensity profiles, with wavelength information when available
- Add ability to export the profile view as a CSV file
- By default, Sunscan instrument shouldn't use altaz mode

### 3.2.1

- Fixed autostretch to be less aggressive on prominences, providing a better transition between the disk and the prominences
- Fixed angle P correction being wrongly applied to spectrum images in flare/ellerman bombs detection
- Process 2 files in parallel in batch mode
- Fixed position of ellipse when image is rescaled
- Fixed clipped disk when trimming SER files
- Reduced false positives in Ellerman Bomb detection

### 3.2.0

- Added [Ellerman Bomb detection](#ellerman-bomb-detection)
- Improved capabilities of the `DRAW_TEXT` function
- Adjusted default parameters
- Fixed colorized image being too dark / truncated
- Added `%PIXEL_SHIFT%` to file naming strategy
- Fixed overlapping process parameters text in batch mode

### 3.1.3

- Fixed technical card not oriented properly when angle P is corrected

### 3.1.2

- Added new "style" for drawing the globe, where the N label matches the celestial north and P the solar north. In addition, if the P angle is corrected, then the grid is also corrected.
- Fixed error in mosaic composition
- Fixed bug when using functions where an unknown pixel shift image would fail the scripts

### 3.1.1

- Improvements to jagging edges correction
- Fixed banding correction possibly causing white artifacts at the extremes of the poles
- Fixed ellipse detection when the image contains bands where the value of pixels is truncated to 0

### 3.1.0

- Added experimental [jagged edges correction](#jagged-edges-correction)
- Fixed the `saturate` function that no longer used the chosen value
- Fixed banding correction on edges

### 3.0.4

- Fixed a performance issue when redshift measurement was enabled
- Fixed position of active areas when rotation is applied
- Fixed non-deterministic redshift/active area detection

### 3.0.3

- Fixed a bug when an image is rescaled, causing misalignments, which could lead to bad stacking results, or incorrect masking of the disk
- Make it possible to show the globe or not in the measurement window
- Add undo/redo in the measurement window
- fix flips and rotations not applied to the measurement window

### 3.0.2

- Fixed incorrect image being saved: the displayed image would have a different stretch than what was shown in the preview

### 3.0.1

- Disabled enhancement of prominences by default, there is now a parameter to enable it
- Fixed typos in translations
- Make darkest line detection more robust

## Ellerman Bomb Detection

Ellerman Bombs are small, transient brightenings that occur in the solar atmosphere, often associated with solar flares or active regions.
They are typically observed in the wings of the H-alpha line and can be indicative of magnetic reconnection events, with a maximum around 1Å from the center of the H-alpha line.
These are very small features, typically ~300km in size, and are often difficult to detect.

JSol'Ex 3.2 introduces a new feature to detect these features automatically.
If the detection is enabled (either by selecting the option in the custom images or by using full processing mode), and that you are processing an H-alpha image, then detection will happen.

If a detection is made, at least 2 images will be created:

- one spectrum image per detected Ellerman Bomb, where a red square will be drawn around the detected area
- a disk image where the detected Ellerman Bombs will be shown as red squares

You should always consider the results of detection with caution, as the algorithm is not perfect and may produce false positives or miss some features.

## Jagged Edges Correction

This feature is experimental and may not work perfectly.
Images captured using a spectroheliograph will often show so-called "jagged edges" at the solar limb: these are due to multiple causes: atmospheric turbulence, wind or imperfect tracking.
JSol'Ex 3.1 introduces a new feature to correct these jagged edges, which will also reduce the mislalignment of features on the solar disk.
To enable the correction, you have to check the "jagged edges correction" option in the image enhancement settings.
The sigma value can be used to adjust the correction: the higher the value, the less restrictive will be the samples used to compute the correction.

## Image Improvements

Image quality has been improved thanks to several changes:

- added background neutralization, which helps remove gradients caused by light reflections
- better enhancement of prominences
- more stable image brightness
- improved automatic extraction algorithm for the Helium line

The sky background calculation algorithm is also available in scripts via the `bg_model` function.

## New Image Stretching Modes

When an image is displayed, you can now choose between several stretching modes:

- linear mode, the default, is the one used before this version
- curve mode, new in this version, allows applying a nonlinear transformation: by providing 2 input and output values, the algorithm computes a second-degree polynomial passing through the points (0,0), (in,out), and (255,255), and applies this polynomial to each pixel
- no transformation, which allows displaying the image without any stretching

## Physical Flat Correction

This feature is particularly useful for spectroheliographs using long slits (7mm or more) or long focal lengths (500mm and above).  
In these cases, it's common to observe darkening at the solar poles, which is undesirable.  
JSol'Ex previously offered a mathematical solution, which sometimes has its limitations.  
This version introduces a new possibility: using physical flats.  
This involves taking a series of images of the sun, with a light diffuser in front of the instrument (such as tracing paper).  
The software will automatically compute a solar illumination model, which can then be applied to correct your images.

Note that this solution may also be useful for Sol'Ex, at certain wavelengths where diffusion is especially visible, such as sodium or H-beta.

## Distance Measurement

This version includes a new distance measurement tool.  
It allows you to estimate, for example, the size of prominences or filaments.  
To do this, simply click the measurement button, or right-click on the image.  
A new window will open, showing the solar grid. You can then click on points in the image, to create a path along a filament for example.  
The software will calculate the distance, taking into account the curvature of the sun (for points on the solar disk).  
For points outside the disk, a simple linear measurement is used.

Note that the distances are approximate: it is not possible to precisely know the height of the observed features, so measurements use an average solar radius value.

## ImageMath Improvements

This new version has significantly improved the ImageMath module and its documentation.  
You can now:

- create your own functions
- import scripts into another script
- call an external web service to generate a script or images
- write function calls across multiple lines
- use named parameters

New functions have also been added:

- `bg_model`: background sky modeling
- `a2px` and `px2a`: conversion between pixels and Angstroms
- `wavelen`: returns the wavelength of an image, based on its pixel shift, dispersion, and reference wavelength
- `remote_scriptgen`: allows calling an external web service to generate a script or images
- `transition`: creates a transition between two or more images
- `curve_transform`: applies a transformation to the image based on a curve
- `equalize`: equalizes the histograms of a series of images so that they look similar in brightness and contrast

And others have been improved:

- `find_shift`: added an optional parameter for the reference wavelength
- `continuum`: improved function reliability, enhancing Helium line extraction

⚠️ Note: Some breaking changes compared to previous versions:

- variables defined in the "simple" part of a script are no longer available in the `[[batch]]` section: they must be redefined.
- the `dedistort` function has reversed the position of the reference parameter and the image list when reusing previously calculated distortions, to be consistent with the simple use case:

## Bug Fixes

This version fixes several bugs:

- Fixed a bug where the detected ellipse was not reused when computing images at different offsets, which could cause disks or images of varying sizes
- Fixed histogram not opening properly in a new window

## Message to US citizen and far right supporters

**If you support Trump or any other party close to the far right, I ask you not to use this software.**

My values are fundamentally opposed to those of these parties, and I do not wish for my work, which I have developed during evenings and weekends, and despite it being open source, to be used by people who support nauseating ideas.

Solidarity, openness to others, ecology, fight against discrimination and inequality, respect for all religions, genders, and sexual orientations are the values that drive me.
I do not accept that my work be used by people who are responsible for suffering and exclusion.
If you do, I kindly ask you to review your choices and turn to more positive values, where your well-being does not come from the rejection of others.
