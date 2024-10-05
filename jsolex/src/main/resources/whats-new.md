# Welcome to JSol'Ex {{version}}!

## Changes in 2.7.1

- Added MacOS x86 installer
- Improved artificial flat correction
- Added `flat_correction` ImageMath function

## Changes in 2.7.0

- Fixed auto-contrast being a bit too agressive on prominences
- Improve quality of reconstruction using bilinear interpolation instead of linear
- Enhanced reconstruction view which allows finding which frame is the source for a particular pixel (issue #386)
- Improve spectral line identification in spectrum browser
- Add `filter` ImageMath function, which allows filtering a list of images, for example to keep only these which are after a certain date
- Add a memory usage limit parameter for reconstruction
