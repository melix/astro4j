# Welcome to JSol'Ex {{version}}!

## What's New in Version 5.3.5

- You can now add more files to a finished batch without reprocessing the ones already done.
- A batch can now watch a directory and automatically add the new SER files which appear in it.
- Fixed the last file of a batch showing all the other files after reviewing images.
- A new "Best method" contrast enhancement option automatically picks the best technique for the detected spectral line: CLAHE for calcium and Autostretch for everything else.
- Stacked images now keep the spectral line of the source images instead of falling back to the wrong wavelength.
- Images produced by the Stacking tool can now be shared to SpectroSolHub.

## What's New in Version 5.3.4

- A new "saturated disk mode" lets you reuse the spectral line polynomial from the closest non-saturated scan when the solar disk is overexposed.
- A new `deghost` scripting function attenuates the ghost reflection of the solar disk caused by optical reflections.
- The spectrum browser can now be overlaid on your capture software, with adjustable transparency and spectral curvature matching.
- The spectrum browser window can now be made borderless with its controls moved to the bottom, for cleaner overlays at the top of the screen.
- Fixed line artifacts that could appear when stacking with local dedistortion enabled.
- Animations and videos are now grouped into the same per-run sections as images when re-running a script.
- A section in the image list can now be closed at once using the close button next to its title.
- The SER trimmer now shows a progress bar again while trimming, and its dialog layout was fixed.
- Parameters of Python scripts are now shown in the order they are declared in the script.

## What's New in Version 5.3.3

- Fixed Helium D3 images that were not generated in some cases when the reference line was not in the middle of the frame.

## What's New in Version 5.3.2

- You can now export the current images to a session file and reopen it later to share your results without reprocessing the original video.
- Cropping in the image viewer is now interactive, with common actions available from the toolbar.
- A new experimental correction can detect and remove regular wave patterns on the solar limb caused by mount oscillations.
- Other known spectral lines present in the capture window are now extracted automatically.
- The 3D spectral views now offer an "Invert" option and an opacity slider to better reveal dark features such as sunspots.
- Monochrome images can now be colorized directly in the viewer, from the annotations panel, using a spectral profile or a custom color.
- The interrupt button now reliably stops processing.
- Optimized GPU acceleration

## What's New in Version 5.3.1

- Faster image processing, including a quicker first run after launching the application.
- The reconstruction view can now be navigated pixel by pixel with the arrow keys (hold Shift for larger steps).
- Added an active regions annotation layer.
- Added free-text annotations.
- When saving an annotated image, you can now keep the original untouched and save the annotations on a copy instead.
- The Save button now stands out when an image has unsaved changes.
- Fixed a duplicate reconstruction view sometimes appearing for the same pixel shift.

## What's New in Version 5.3.0

- Replaced the standalone Technical Card image with interactive annotations toggled per image: orientation grid, observation details, solar parameters, prominence scale, drag-and-place Earth size reference and a free-text signature, all with customisable colors, line thickness and templates, savable as a preset.
- Images can now be duplicated from the sidebar right-click menu so you can keep an unannotated original alongside an annotated copy.
- Redshift measurements are more reliable and their uncertainty is much more realistic.
- Added a new `average_image` ImageMath function which returns the average image, optionally distortion-corrected.
- Fixed a bug which caused log files to be empty in batch mode
- Fixed a crash when generating redshift animations and panels
- Added progress reporting while generating redshift animations and panels
- Reorganised the right-hand panel with a labelled panel selector and a dedicated image publishing panel.
- Fixed the spectrum line marker in the reconstruction view being misplaced
- The spectrum profile now scales the frame and clicked-line intensities against the average image
- The spectral profile tab can now display absolute intensities (ADU) instead of values normalized to 100
- The GONG reference image tab now lets you pick the observatory and the image size, up to full resolution
- The GONG reference image tab can detect possible flips of your active image compared to the GONG reference
- Refreshed the main window with a consistent color theme
- Re-running a script now groups its results into collapsible, numbered run sections so successive runs are easy to tell apart and compare

## What's New in Version 5.2.2

- The BASS2000 submission wizard now lists pending submissions from other observers for the same date and wavelength, with a visual timeline showing when they were captured.
- Redshift speeds are now computed with subpixel precision and reported with an uncertainty.
- Fixed the ImageMath script run button losing its advanced parameters dropdown after starting a batch.
- Fixed a native memory leak in GPU image uploads that could exhaust memory after several batch runs.
- Fixed the reconstruction view drawing the spectrum cross on the wrong side of the reference line when clicking on the solar image.
- Fixed an error that could occur when saving certain debug images.

## What's New in Version 5.2.1

- Fixed an error that could prevent the inverted image from being generated when the solar disk was small within the frame.
- The inverted image no longer displays the observation details overlay.
- SunScan import now skips scans already present in the download folder instead of downloading them again.
- Reduced memory usage when running batch processing several times in a row, which could previously lead to out-of-memory errors.

## What's New in Version 5.2.0

- Redesigned the batch processing view with a progress dashboard, compact rows and a detail side panel.
- Batch files are now processed in submission order.
- Autostretch produces a smoother, more natural brightness, preserving detail in active regions without crushing prominences or the limb fade.
- The inverted image now inverts only the solar disk with enhanced contrast on its features, while keeping the prominences in their natural orientation.
- Colorized images now use a more even tonal range thanks to a revised stretching pipeline.
- Images in the sidebar can now be renamed via the right-click menu to disambiguate them in collages.
- The `dedistort` ImageMath function gained a `drizzle` parameter (any value between 1 and 4, e.g. 1.5, 2, 3) that produces a super-resolved output to recover detail under excellent seeing.
- Scans acquired on a SunScan device can be discovered and imported directly from the File menu.
- Fixed some generated images being missing from batch results and the image review window.
- Double-clicking an image to zoom in at 1:1 now centers the viewport on the clicked point.
I do- Added an advanced parameter to choose the directory where temporary files are written.
- Fixed the reconstruction preview occasionally not being contrast-adjusted during processing.
- Added a logs activity indicator so background work is visible from any tab.

## What's New in Version 5.1.3

- Video Analyzer: added a play button with a speed slider (up to 300 fps) to play frames as a video.
- Faster SER file processing thanks to reduced memory copies when reading frames.
- Fixed custom color curves being silently lost after upgrading from a previous version.
- Added a "Trust SER file bit depth" option in the advanced process parameters to bypass automatic bit depth detection when it produces an incorrect result.
- Fixed the 3D viewers (single image 3D view and spherical tomography, both shell and smooth modes) failing to render on macOS.
- Added a "Go to coordinates..." entry in the reconstruction view right-click menu to jump to a specific (X, Y) without having to click on the image.

## What's New in Version 5.1.2

- Added a new ImageMath function `clahe2` that averages several CLAHE passes at tile sizes adapted to the solar disk, producing much softer limb artifacts than classic CLAHE.
- Fixed the default sharpening kernel size shown when picking "Sharpen" or "Unsharp mask".
- Mosaic composition now uses a multi-band (Laplacian pyramid) blend with local exposure equalization in the overlap area.
- Stacking &amp; mosaic wizard: a new "Stacking method" choice lets you pick between "Fast" and "Consensus".
- Fixed a crash during stacking (`Progress must be between 0.0 and 1.0`) when the image height was not a multiple of the tile increment.
- Fixed slow Next/Previous navigation and slow opening of the batch image review wizard.
- Fixed the batch image review wizard's reference panel briefly showing the wrong image kind after navigating.
- Fixed inconsistent image ordering in the batch image review wizard side list across SER files.
- Fixed the collage created via Tools | Load images being saved inside the JSol'Ex installation folder; it now lands in the directory of the loaded images.
- Fixed the collage dialog occasionally opening with an empty image strip or freezing the UI while loading thumbnails.
- Fixed "Open in new window" producing an empty popup that could not be closed when used on an image opened via Tools | Load images.
- Fixed the ImageMath `sort` function returning the unsorted list when the order argument used the `asc` suffix (e.g. `sort(images, "date asc")`).
- Fixed `rotate_deg`, `rotate_left` and `rotate_right` ImageMath functions failing or rotating by the wrong angle when applied to a list of images.
- Fixed `dedistort` failing on lists of images with different sizes; frames are now auto-aligned beforehand.
- `draw_text` placeholders now use each image's own metadata when available.
- Added `copy_metadata(to: ...; from: ...)` to copy observation metadata between images.

## What's New in Version 5.1.1

- SpectroSolHub Live: progress events are now displayed while images are being uploaded
- SpectroSolHub Live: raw and reconstruction images are no longer uploaded
- SpectroSolHub Live: in batch mode, only images produced by the `[[batch]]` output section of scripts are uploaded. A warning is logged at the end of a batch if no image was uploaded.
- Orientation wizards (BASS2000, SpectroSolHub): the GONG reference image can be switched to another observatory via a picker overlaid on the image, and auto-align can be restricted to rotation-only when the image is already correctly flipped.
- Fixed image viewer alignment not being cancelled when clicking a zoom button (+, -, 1:1, fit) after having activated image alignment.
- Fixed batch processing starting many more files in parallel than the configured limit, which could exhaust memory on large batches.
- Fixed the batch processing table occasionally displaying the wrong row's content (progress bar, redshift, or generated image links) when scrolling through many files.
- ImageMath: the `meta { ... }` block now supports an optional `requirements { images = [...] }` section that ensures the listed image kinds are generated automatically when the script runs.
- ImageMath: negative default values (e.g. `default = -1`) are now accepted in the `meta { params { ... } }` block.

## What's New in Version 5.1.0

- Added SpectroSolHub Live mode: stream your processing session in real-time for others to watch at spectrosolhub.com/live. Accessible from the Sharing menu.
- All images can now be uploaded to SpectroSolHub, not just those where the solar disk was detected
- Fixed script repositories with spaces in script filenames failing to download
- Improved error messages when loading SpectroSolHub repositories fails
- Re-running an ImageMath script after tweaking a parameter is now much faster: only expressions that depend on the changed parameter are recomputed.
- Added two ImageMath functions, `scale_to_unit` and `scale_from_unit`, to convert pixel values between the [0;65535] and [0;1] ranges (with optional clamping).
- Improved `dedistort` quality on multi-iteration consensus runs via adaptive per-tile convergence.
- Added keyboard shortcuts in the image inspector.

## What's New in Version 5.0.4

- Fixed a performance issue in Helium extraction when GPU acceleration was disabled
- Fixed a thread starvation issue that could lead to incomplete processing
- Added auto-alignment in BASS2000 submission wizard

## What's New in Version 5.0.3

- Added a new "Sharing" menu with items for publishing to BASS2000 and sharing on SpectroSolHub
- Added the ability to post-process images externally before uploading to SpectroSolHub. In the image selection step, check the post-processing option to edit your images (contrast, sharpening, etc.) in your favorite editor before upload.
- Fixed non deterministic dedistorsion results
- Improved performance on multi-core systems
- Reworked Helium line image extraction
- Added the ability to declare the type of image generated in scripts
- Improved the ergonomics of the SpectroSolHub publishing wizard

## What's New in Version 5.0.2

- Added the ability to configure the level of parallelism used in batch processing mode
- Fixed GPU acceleration failing on devices with limited work group sizes, causing unnecessary fallback to CPU
- When a previous OpenGL crash is detected, a dialog now offers the option to retry instead of silently disabling the 3D viewer

## What's New in Version 5.0.1

- Fixed SpectroSolHub image upload not applying transformations (such as P angle correction) to images that were not individually viewed before uploading
- Fixed SpectroSolHub orientation comparison step not applying P angle correction to the user image when comparing with the GONG reference

## What's New in Version 5.0.0

- [SpectroSolHub Integration](#spectrosolhub-integration)
- [Python Scripting](#python-scripting)
- [Doppler Rotation Correction](#doppler-rotation-correction)
- [Spectrum Browser Improvements](#spectrum-browser-improvements)
- [New ImageMath Functions](#new-imagemath-functions)
- [Bug Fixes and Improvements](#bug-fixes-and-improvements)

## SpectroSolHub Integration

[SpectroSolHub](https://spectrosolhub.com) is a newly released companion to JSol'Ex, currently in beta. It is a service which lets you share spectroheliographic images with the community. It is written by the author of JSol'Ex.

![SpectroSolHub](/docs/spectrosolhub.png)

You can now publish your processed images directly to SpectroSolHub from JSol'Ex. After processing a SER file, click the "SpectroSolHub" button in the status bar to open the publishing wizard. The wizard guides you through authentication, image selection, session metadata, and upload.

You can also browse and add script repositories from SpectroSolHub directly from the script repositories manager. Click "Browse SpectroSolHub" to discover available script repositories and add them with a single click.

**Note:** By uploading images to SpectroSolHub, you grant a non-exclusive, worldwide, royalty-free license to use your uploaded content for scientific research purposes.

## Python Scripting

JSol'Ex 5.0 introduces Python scripting, a major step forward in extensibility.
You can now write image processing scripts in Python, in addition to the existing ImageMath language.
Python scripts have access to the full processing pipeline and can leverage the rich Python ecosystem for advanced analysis.

Refer to the documentation for details on how to write and use Python scripts.

## Doppler Rotation Correction

JSol'Ex 5.0 introduces a new "Doppler (rotation corrected)" image type.
This generates a Doppler image with the smooth solar rotation gradient removed using 2D polynomial fitting, making chromospheric velocity features much easier to see.

Two new ImageMath functions support custom Doppler correction workflows: `SIGNED_DIFF(a, b)` computes the difference between two images preserving sign (without normalization), while `POLY_FIT_2D(image, degree)` fits a 2D polynomial surface within the solar disk.

## Spectrum Browser Improvements

The spectrum browser has been significantly improved in this release:

- Wavelength grid labels now auto-adjust to the zoom level, with minor tick marks and faint grid lines for easier reading
- New zoom level presets (25%, 50%, 75%, 100%, 150%, 200%, 400%) relative to instrument dispersion

## New ImageMath Functions

- `COLLAGE`: create image layouts using a text pattern (e.g., `collage(".X. / X.X", images)` for a pyramid layout)
- `SIGNED_DIFF(a, b)`: computes the difference between two images preserving sign
- `POLY_FIT_2D(image, degree)`: fits a 2D polynomial surface within the solar disk

## Bug Fixes and Improvements

- Fixed a memory leak that would be visible during reconstruction with very large SER files

## Message to US citizen and far right supporters

**If you support Trump or any other party close to the far right, I ask you not to use this software.**

My values are fundamentally opposed to those of these parties, and I do not wish for my work, which I have developed during evenings and weekends, and despite it being open source, to be used by people who support nauseating ideas.

Solidarity, openness to others, ecology, fight against discrimination and inequality, respect for all religions, genders, and sexual orientations are the values that drive me.
I do not accept that my work be used by people who are responsible for suffering and exclusion.
If you do, I kindly ask you to review your choices and turn to more positive values, where your well-being does not come from the rejection of others.
