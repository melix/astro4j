# Welcome to JSol'Ex {{version}}!

## What's New in Version 2.9.0

- Added detection of active regions
- Automatically label detected active regions
- Add ability to process SER files to the standalone scripting module
- Parallactic angle correction for Alt-Az mounts
- Add button to delete SER file after processing
- 
### Active Regions Detection

This version introduces detection of active regions in solar images.
The detection works by analyzing each frame in your SER file, and identifying regions with unexpected brightness compared to a model of what it should look like.
Active regions detection will generate 2 images:

- one of the studied line with an overlay of the detected active regions
- one with the continuum image of the studied line, with the detected active regions highlighted

The detection mechanism is responsible for detecting the "areas" which cover sunspots.
However, if you have access to internet, it will also be able to put labels onto those active areas, to help you identify them.
Note, however, that the label positions will only be correct if the image is oriented correctly (horizontal/vertical flips) and that the time and date are correctly set in your SER file.

You can also generate more images by using the scripts functions which are described below.

### Alt-Az Mode and parallactic angle correction

Until this version, JSol'Ex assumed that the images were captured using an equatorial mount.
As such, the orientation grid and the P-angle correction were assuming that you were using an equatorial mount.
With this version, you have the option to check the "Alt-Az" mode in the processing parameters, as well as declaring a setup as using an Alt-Az mount.
This is for example your case if you are using a Sunscan, or a Sol'Ex in Sunscan mode.
In this case, we recommend that you also enter the latitude and longitude of the observation, since JSol'Ex will automatically perform parallactic angle correction.
This will make sure that the image is oriented correctly (except for horizontal/vertical flips), that the P-angle correction is done correctly, and that the labels for active regions are correctly positioned.

### New Functions in Scripts

The following functions have been added:

- `GET_AT` allows fetching an image in a list at a specific index.
- `AR_OVERLAY` generates an overlay of active regions on an image.
- `CROP_AR` crops an image to the detected active regions.

### Bug Fixes and Improvements

- Fixed a bug where an error message could be displayed during reconstruction, with no effet on the result.
- Fixed SER files not being closed, preventing them from being deleted under Windows

## Message to US citizen and far right supporters

**If you support Trump or any other party close to the far right, I ask you not to use this software.**

My values are fundamentally opposed to those of these parties, and I do not wish for my work, which I have developed during evenings and weekends, and despite it being open source, to be used by people who support nauseating ideas.

Solidarity, openness to others, ecology, fight against discrimination and inequality, respect for all religions, genders, and sexual orientations are the values that drive me.
I do not accept that my work be used by people who are responsible for suffering and exclusion.
If you do, I kindly ask you to review your choices and turn to more positive values, where your well-being does not come from the rejection of others.
