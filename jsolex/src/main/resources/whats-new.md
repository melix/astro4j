# Welcome to JSol'Ex {{version}}!

## What's New in Version 2.11.2

- Made SER file pixel depth detection more robust
- Added geometry corrected image (no contrast enhancement) to the quick mode
- Improve performance of SER conversion
- Let user use decimal numbers for focal length and slew rates

## What's New in Version 2.11.1

- Added a description to the generated images
- Fixed loading of TIF images in the image reviewing tool
- Fixed selection of images in the list of the reviewing tool which could be lost when clicking next/previous

## What's New in Version 2.11.0

- Added an image reviewing tool to quickly browse through processed images in batch mode and reject bad scans
- Improve responsiveness of the UI

## What's New in Version 2.10.1

- Improved handling of temporary files to avoid them accumulating
- Fixed `continuum` function which could fail in some rare cases
- Support binary operation on lists of same size: for example min(list1, list2) applies `min` on each element of the lists
- Added `concat` function to concatenate lists
- Save `CENTER_X`, `CENTER_Y` and `SOLAR_R` in FITS header for INTI compatibility

## What's New in Version 2.10.0

- Added ability to [trim the processed SER files](#trimming-processed-ser-files)
- Added an embedded [web server](#embedded-web-server)
- Added convolution kernel size for `SHARPEN` and `BLUR` functions
- Fixed rare bug where ellipse fitting would fail despite being detected
- Process files sequentially in batch mode instead of concurrently to reduce pressure on lower end machines

### Trimming Processed SER Files

Sometimes, for practical reasons, the SER files that you are processing may contain a lot of empty frames at the beginning or end of the file, for example if you are using a mount that takes a few seconds to stabilize before starting the acquisition.
Sometimes, you may also have used a large cropping window, which contains significantly more spectral lines than useful for the processing.

In these cases, it is possible to trim the processed SER files to remove these empty frames and reduce the size of the files.
At the end of processing, the "Trim SER file" button will be enabled, and will suggest a range of frames to keep, as well as a portion of each frame to crop.
These are based on the detection of the solar disk in the frames, with a 10% margin.
In addition, the trimmed SER file will have "smile" correction applied, which means that all lines will be perfectly horizontal.
You have the option to choose how many pixels you want to keep around the center line.

## Embedded Web Server

JSol'Ex is sometimes used in public settings, where it can be interesting to show the sun live.
For this purpose, JSol'Ex offered an option to open images in a separate window (by right-clicking on it), which would make it possible, for example, to move that window on an external screen.
However, the limitation is that your computer has to be wired to an external monitor.
With this release, a new, simplified UI is available via the "Tools" menu.
You can start an embedded web server which will serve images being processed.
The advantage is that you can share the URL to the server to people on the same network: each of them can see the images on their own devices.
This also makes it possible to use an external screen from a remote computer.

The web server listens by default on port `9122`, and can be activated by going to the "Tools" menu.

## Message to US citizen and far right supporters

**If you support Trump or any other party close to the far right, I ask you not to use this software.**

My values are fundamentally opposed to those of these parties, and I do not wish for my work, which I have developed during evenings and weekends, and despite it being open source, to be used by people who support nauseating ideas.

Solidarity, openness to others, ecology, fight against discrimination and inequality, respect for all religions, genders, and sexual orientations are the values that drive me.
I do not accept that my work be used by people who are responsible for suffering and exclusion.
If you do, I kindly ask you to review your choices and turn to more positive values, where your well-being does not come from the rejection of others.
