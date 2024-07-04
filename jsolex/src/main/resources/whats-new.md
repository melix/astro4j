# Welcome to JSol'Ex {{version}}!

Here are the new features in this version:

- [Spectrum browser](spectrum-browser)
- [New ImageMath functions](#new-ImageMath-functions)
- [Bugfixes and improvements](#bugfixes-and-improvements)

## Spectrum browser

The spectrum browser is available via the Tools menu.
It displays an image of the spectrum as it would be seen through your favorite capture software (SharpCap, FireCapture, ...) and lets you scroll and zoom in/out.

The spectrum will display remarkable spectral lines and let you search for particular wavelengths.
It optionally provides a colorized version of the spectrum, so that you get an idea "where" in the visible spectrum of light you are.

This browser also features an experimental idenfication feature, inspired by [INTI Map](http://valerie.desnoux.free.fr/inti/map.html).
You can basically select an image of the spectrum that you captured and JSol'Ex will try to find where it fits in the spectrum.
This can be particularly useful for learning purposes, but also in order to find lines which are harder to identify visually.

## New ImageMath functions

- the `draw_earth` function has been added to draw the Earth on an image, scaled accordingly to the solar disk

## Bugfixes and improvements

- Added a default file naming pattern for batch mode
- Added warning when requested pixel shift isn't available
- Replace invalid pixel shifts with best fit
- Display required disk space when generating custom animations
- Renamed "Spectrum Debugger" to "Video Analyzer" for clarity
