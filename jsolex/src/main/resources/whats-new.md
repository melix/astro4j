# Welcome to JSol'Ex {{version}}!

Here are the new features in this version:

- [Automatic Spectral Line Detection](#automatic-detection-of-the-studied-line)
- [Automatic Coloring Based on Wavelength](#automatic-coloring-based-on-wavelength)
- [Reading Sharpcap and Firecapture Metadata](#reading-sharpcap-and-firecapture-metadata)

## Automatic Detection of the Studied Line

JSol'Ex now offers automatic detection of the studied line, as well as camera binning.
To ensure correct detection, it is important to **enter the pixel size of your camera** correctly in the "observation details" section.
It's worth noting that detection will work better with a larger cropping window and will probably fail if the spectrum is too saturated.

Finally, if the "automatic" mode does not appear in the list of available lines, it means you have added or modified lines in the editor, in which case you will need to [reset the available lines](#reset-available-lines) for the "automatic" mode to appear.
When a line is selected, the profile tab now displays the measured profile compared to a reference profile.

## Reading Sharpcap and Firecapture Metadata

If a Sharpcap or Firecapture metadata file is found next to the SER file, it will be read, allowing JSol'Ex to automatically populate the "Camera" and "Binning" fields.
Note that the pixel size of the camera **must** be entered manually in the "observation details" section.
Sharpcap files will be detected if they have the same name as the SER file but with the extension `.CameraSettings.txt`.
Firecapture files will be detected if they have the same name as the SER file but with the extension `.txt`.

## Automatic Coloring Based on Wavelength

In previous versions, automatic coloring was only available if you defined a coloring curve.
Now, automatic coloring will estimate the color automatically.
However, you can replace the automatic color with a manual curve if the result does not satisfy you.

## Miscellaneous
### Minor fixes and improvements

- Added new image fitting buttons for zooming and centering images
- Improved colorization algorithm

### Reset Available Lines

JSol'Ex allows you to manually add lines to the predefined list or customize coloring curves.
If you have made such modifications in the past, the "automatic" mode will not be available by default.
You have two options to add it. In both cases, open the spectral line editor through the "Tools" menu and then:

- Manually add an entry named `Autodetect` with a wavelength of 0 nm to the list of available lines.
- Or click on "reset to default"

The latter option is the simplest, as it will restore the predefined lines and the "automatic" mode, while also allowing you to benefit from the new lines added in this version.
