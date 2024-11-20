# Welcome to JSol'Ex {{version}}!

## Changes in 2.8.0

- Fixed bug in alignment button which didn't always sync zoom and position
- Added `GET_R`, `GET_G`, `GET_B` and `MONO` functions which respectively extract the R, G and B channels of an image, and converts it to mono for the last one
- Fixed loading of 8-bit mono JPEGs which didn't account for gamma correction
- Improved stacking stability
- Internal image format changes to make future changes easier to implement
- Added module to run scripts from command-line
- Upgrade to Java 23
