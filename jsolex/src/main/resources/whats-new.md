# Welcome to JSol'Ex {{version}}!

## Changes in 2.7.4

- Fixed bug in alignment button which didn't always sync zoom and position

## Changes in 2.7.3

- Fix polynomial detection when multiple dark lines are found in crop window
- Strech colors of reconstruction view when reconstruction is done
- Add a "select and process" file which doesn't even ask for process params

## Changes in 2.7.2

- Fixed reconstruction bug causing artifacts particularly visible in Helium
- Added `disk_mask` function which allows creating a mask of the solar disk
- Added a new ImageMath sample for enhanced inverted image creation
- Fixed ImageMath "save" button which didn't remember the directory used

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
