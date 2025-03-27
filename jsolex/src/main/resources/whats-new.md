# Welcome to JSol'Ex {{version}}!

# What's New in Version 3.0.0

- Add ability to disable stretching in image display
- Add background neutralization to the automatic contrast enhancement image to remove residual reflections
- Improve prominence stretching in automatic contrast enhancement
- Introduced new `BG_MODEL` function to model the background of the image
- Added `A2PX` and `PX2A` to convert from Angstroms to pixels and vice versa, based on the computed spectral dispersion
- Added an optional parameter to `FIND_SHIFT` corresponding to the reference wavelength
- Improve reliability of the `CONTINUUM` function and as a result of the Helium line extraction
- Added `WAVELEN` function which returns the wavelength of an image, based on its pixel shift, dispersion and reference wavelength
- Added `REMOTE_SCRIPTGEN` function for advanced scripting capabilities
- Added `TRANSITION` function to create a transition between two or more images
- Fixed bug where the detected ellipse wasn't reused when computing images at different shifts, which could cause disks or images of different sizes
- Fixed histogram not opening correctly in a new window

## Message to US citizen and far right supporters

**If you support Trump or any other party close to the far right, I ask you not to use this software.**

My values are fundamentally opposed to those of these parties, and I do not wish for my work, which I have developed during evenings and weekends, and despite it being open source, to be used by people who support nauseating ideas.

Solidarity, openness to others, ecology, fight against discrimination and inequality, respect for all religions, genders, and sexual orientations are the values that drive me.
I do not accept that my work be used by people who are responsible for suffering and exclusion.
If you do, I kindly ask you to review your choices and turn to more positive values, where your well-being does not come from the rejection of others.
