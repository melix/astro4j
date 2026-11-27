# Welcome to JSol'Ex {{version}}!

## What's New in Version 6.0.0

- [Easier Getting Started](#easier-getting-started)
- [Adjustments in the Image Viewer](#adjustments-in-the-image-viewer)
- [Ellerman Bombs and Flares](#ellerman-bombs-and-flares)
- [Python Scripting](#python-scripting)
- [Bug Fixes and Improvements](#bug-fixes-and-improvements)

## Easier Getting Started

JSol'Ex is easier to discover. On first launch, an assistant lets you process your first file without exploring the options. It reads the camera from the settings file of your capture software and lets you choose your spectroheliograph from pictures. The telescope, the mount and the observer are optional.

![Setup assistant](/docs/setup-assistant-en.png)

The home screen now shows the settings which will be used for processing, and lets you go back to the automatic settings. Before processing, JSol'Ex warns you if the file was recorded with a different camera from the one in your equipment.

After the first processing, a guided tour presents the different areas of the screen. It remains available from the "Help" menu.

![Guided tour](/docs/guided-tour-en.jpg)

## Adjustments in the Image Viewer

The image viewer leaves more room for the image: the buttons fit on two rows, with coloured icons, and the adjustments open in a panel next to the image. It can now blur and sharpen images, including with an unsharp mask. Image stretching is also faster, in particular with CLAHE, CLAHE2 and AutoStretch.

![Image viewer](/docs/image-viewer-en.jpg)

## Ellerman Bombs and Flares

The detection of Ellerman bombs is more reliable. It is based on the approach described by Stephan Schmelzing. Each detected Ellerman bomb or flare now comes with a chart comparing its line profile with its surroundings.

## Python Scripting

**Python virtual environments must now be created with GraalPy 25.6.** If you use one, create it again with this version.

- Faster conversion of images to and from NumPy.
- The standalone scripting tool can now use a GraalPy virtual environment.
- NumPy can now be used on Windows.
- Scripts can save files of their own, such as CSV files, alongside the other outputs.

## Bug Fixes and Improvements

- Saturated disk mode no longer needs reference scans: the spectral line can be found on the saturated scan itself.
- Lower memory use during processing.
- The "Memory consumption adjustment" setting has been removed, since it is no longer needed.
- Fixed white corners on images rotated to correct the P angle.
- Fixed the standalone scripting tool failing on computers with a graphics card.
- Fixed Python scripts in the native version of the standalone scripting tool.
- Fixed errors which could stop processing when a new file was opened or an image was displayed.
- Fixed an error when creating an animation or a panel before processing had finished.

## Message to US citizen and far right supporters

**If you support Trump or any other party close to the far right, I ask you not to use this software.**

My values are fundamentally opposed to those of these parties, and I do not wish for my work, which I have developed during evenings and weekends, and despite it being open source, to be used by people who support nauseating ideas.

Solidarity, openness to others, ecology, fight against discrimination and inequality, respect for all religions, genders, and sexual orientations are the values that drive me.
I do not accept that my work be used by people who are responsible for suffering and exclusion.
If you do, I kindly ask you to review your choices and turn to more positive values, where your well-being does not come from the rejection of others.
