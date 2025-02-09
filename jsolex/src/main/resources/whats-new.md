# Welcome to JSol'Ex {{version}}!

## What's New in Version 2.10.0

- Added ability to [trim the processed SER files](#trimming-processed-ser-files)
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

## Message to US citizen and far right supporters

**If you support Trump or any other party close to the far right, I ask you not to use this software.**

My values are fundamentally opposed to those of these parties, and I do not wish for my work, which I have developed during evenings and weekends, and despite it being open source, to be used by people who support nauseating ideas.

Solidarity, openness to others, ecology, fight against discrimination and inequality, respect for all religions, genders, and sexual orientations are the values that drive me.
I do not accept that my work be used by people who are responsible for suffering and exclusion.
If you do, I kindly ask you to review your choices and turn to more positive values, where your well-being does not come from the rejection of others.
