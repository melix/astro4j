# Welcome to JSol'Ex {{version}}!

Here are the new features in this version:

- [Changes since 2.4.0](#changes-since-2.4.0)
- [Automatic Helium D3 processing](#automatic-detection-of-the-studied-line)
- [Equipment management](#equipment-management)
- [Other spectroheliograghs support](#other-spectroheliographs-support)

## Changes since 2.4.0

- Added ability to save GONG image by right-clicking on the image
- Fixed excessive memory consumption during image reconstruction
- Fixed links to open generated files in batch mode
- Fixed an error when a script tries to overwrite an animation under Windows
- Added ability to continue processing a batch script even if some files are in error
- Fixed detected speeds not being placed correctly on images/animations when tilt angle is not 0
- Improved accuracy of speed detection
- Added `SORT` and `VIDEO_DATETIME` ImageMath functions
- Fixed background neutralization bug on some videos
- Made "continuum shift" configurable
- Added a button to reset process parameters in watch mode
- 
## Automatic Helium D3 processing

Before this release, processing a helium D3 line required manual intervention: in particular, you needed to analyze the SER file using the spectrum debugger to determine the pixel shift between a reference line (usually the Sodium D2 line or the Iron Fe 1 line) and the helium D3 line.

Starting from this release, JSol'Ex can automatically generate Helium line images without any manual intervention, in a single click!

In order to do this, the reference line must either be properly detected (when using the "Autodetect" mode), or you can manually set the reference line in the "Process parameters" window.

It's worth noting that the binning and pixel size of your camera must be properly set in the "Observation details" section for the computation of the pixel shift to be accurrate.

The new images will be automatically generated as soon as you select the "Geometry corrected (processed)" images or "Colorized" images in the image selection tab (this is automatic in quick mode and full mode respectively).

Should the generated Helium image be incorrect, you can still manually generate Helium line images using [ImageMath scripts](https://melix.github.io/astro4j/latest/en/jsolex.html#_imagemath_scripts).

## Equipment management

In previous releases, if you had multiple equipments (e.g different telescopes, different configurations with and without a focal reducer, etc.), you had to manually change the equipment settings each time you wanted to switch between them.
Starting from JSol'Ex 2.4, a new "Equipment" menu has been added, which lets you declare multiple equipments and switch between them easily.

## Other spectroheliographs support

JSol'Ex has always been capable of processing other SER files than these produced by the Sol'Ex instrument, but the computation of the profile wavelengths wasn't accurrate for these.
In this release, we have added support for declaring new spectroheliographs, with their own grating, focal length, etc.
