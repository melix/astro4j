# Welcome to JSol'Ex {{version}}!

## What's New in Version 2.8.1

- Fixed bug in average image calculation which would result in a black image
- Added a button to add known SHGs models which are not listed

## What's New in Version 2.8.0

### Support for Sunscan

The [Sunscan](https://www.sunscan.net/) is the latest innovation from the Staros team.  
The new kid of the Sol'Ex family is a compact, fully automated device that allows you to capture images of the Sun directly from your phone, without the need for a computer.  
However, some users export their SER files to process them with JSol'Ex.  
To facilitate this processing, JSol'Ex 2.8 introduces several new features:

- The ability to invert the spectrum: Sunscan spectrum images have red at the top and blue at the bottom.
- A new stacking algorithm, developed with the help of Christian Buil, makes it easier to stack images from a Sunscan.

This new stacking algorithm replaces the old one, even for non-Sunscan images.  
Adjustments to your scripts may be necessary to account for these changes.  
Additionally, this algorithm enables easier implementation of new features in the future.

### New Functions in Scripts

New functions have been added to simplify image processing:

- `GET_R`, `GET_G`, `GET_B`, and `MONO` allow you to extract the red, green, and blue channels from a color image, and convert a color image to monochrome, respectively.
- `DEDISTORT` corrects image distortion using a reference image. This function was developed by Christian Buil and adapted for JSol'Ex.
- `STACK_REF` allows you to specify a reference image for stacking.

### Bug Fixes and Improvements

- Fixed the "align images" button, which was not always working.
- Reduced memory usage when generating animations or panels.
- Limited panel sizes to 7680x7680 pixels.
- Fixed the double creation of animations when FFMPEG was available.
- Fixed the loading of 8-bit mono JPEG images to correctly account for gamma correction.
- Improved stacking stability.
- Fixed the inversion of latitude/longitude fields.
- Added a module to execute scripts from the command line.
- Added the ability to select annotation colors when creating custom animations.
- Adjusted the Doppler image rendering for colors closer to red/blue.
- Use supersampling in reconstruction to avoid aliasing artifacts when the spectral line is thin
- Upgraded to Java 23.
- Updated internal data structures to facilitate future enhancements.
