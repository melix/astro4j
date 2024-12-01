# Welcome to JSol'Ex {{version}}!

## Changes in 2.8.0

- Fixed bug in alignment button which didn't always sync zoom and position
- Reduced memory pressure when generating animations or panels
- Limited panels size to 7680x7680 pixels
- Fixed duplicate animation creation when FFMPEG was available
- Added `GET_R`, `GET_G`, `GET_B` and `MONO` functions which respectively extract the R, G and B channels of an image, and converts it to mono for the last one
- Fixed loading of 8-bit mono JPEGs which didn't account for gamma correction
- Improved stacking stability
- Internal image format changes to make future changes easier to implement
- Added module to run scripts from command-line
- Fixed inversion of latitude/longitude fields in process params display
- Upgrade to Java 23
