# License

The `visatl-telluric.dat` file is derived from "An Atlas of the Spectrum of the Solar Photosphere from 13,500 to
28,000 cm-1 (3570 to 7405 Å)" (N.S.O. Technical Report #98-001, June 1998) by L. Wallace, K. Hinkle and W. Livingston,
obtained with the Fourier Transform Spectrometer of the McMath/Pierce Solar Telescope at Kitt Peak, and published by the
National Solar Observatory at https://nispdata.nso.edu/ftp/pub/atlas/visatl/

These data are freely available with one restriction: a published paper using the data, or a product based on the data,
must include the following acknowledgement:

    NSO/Kitt Peak FTS data used here were produced by NSF/NOAO.

The file holds the first two columns of the `sp13500` to `sp19950` files, that is the wavenumber as observed (cm-1)
and the telluric spectrum deduced by the authors, with the 3 cm-1 overlaps between segments removed. The other columns,
the observed and telluric-corrected photospheric spectra, are not used: the solar spectrum comes from BASS2000. A value
of -1 marks a point where the telluric spectrum could not be deduced.
