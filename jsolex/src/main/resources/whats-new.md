# Welcome to JSol'Ex {{version}}!

Here are the new features in this version:

- [Custom animations and cropping](#custom-animations-and-cropping)
- [Bugfixes and improvements](#bugfixes-and-improvements)

## Changes in 2.5.4

- Added a spectrum browser
- Added warning when requested pixel shift isn't available
- Replace invalid pixel shifts with best fit
- Display required disk space when generating custom animations

## Changes in 2.5.3

- Improved exposure time calculator
- Added more fields to the custom spectroheliographs editor
- Fixed reading of Firecapture metadata
- Reduced memory usage when analyzing images
- Replaced zoom slider with buttons

## Changes in 2.5.2

- Fixed redshifts position that could be incorrect in case of strong tilt or horizontal/vertical flip
- Fixed double-click not changing zoom
- Improve performance of redshifts animations creation

## Changes in 2.5.1

- Use separate display category for redshifts and promote redshift debug images to this category
- Added possibility to annotate redshift animations
- Display of FPS in the optimal exposure calculator
- Fixed autostretch strategy producing bright images when the original SER file has a large offset
- Fixed contrast adjustment not stretching to the full range
- Fixed min/max pixel shift in custom animations limited to the min of the two
- Fixed missing images in scripts in case of composition with `find_shift` function result
- 
## Custom animations and cropping

It is now possible to crop and image to a manual selection or create animations of a region of a solar disk, or a panel of redshifts, by manually selecting a region of interest.

To do this, in the image view, press "control" then click and drag to select the region of interest.
A menu will appear where you can either select to crop the image to the selection or create an animation/panel of the selected region.

In order for the annotations to be accurate, make sure that:

- you have set the pixel size of your camera in the observation details
- you have either selected "autodetect" in wavelength selection or you have explicitly specified the correct wavelength


## Bugfixes and improvements

- Added the ability to display GPS coordinates in observation details
- Fixed ringing artifacts in autostretched image
- Fixed a bug preventing negative longitude or latitude coordinates
