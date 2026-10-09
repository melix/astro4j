# License

The `moore-lines.csv` file lists solar line identifications from "The Solar Spectrum 2935 Å to 8770 Å: Second Revision
of Rowland's Preliminary Table of Solar Spectrum Wavelengths" by C. E. Moore, M. G. J. Minnaert and J. Houtgast
(National Bureau of Standards Monograph 61, 1966, https://doi.org/10.6028/NBS.MONO.61), a publication of the
United States government in the public domain.

The file is `moore_clean02012026.csv` from the HelioSpectrotron 5000 project by A. Pietrow
(https://github.com/AlexPietrow/HelioSpectrotron5000), published under the Apache License, Version 2.0, and
described in A. G. M. Pietrow (2026), "HelioSpectrotron 5000: An interactive multi-resolution solar spectral atlas"
(https://arxiv.org/abs/2602.20101). The printed tables were digitized with OCR, only the first element was kept for
blends, ambiguous entries were removed, the deepest line of each 0.5 Å interval was selected, and the result was
checked by hand against the monograph. It holds the lines between 3374 Å and 7329 Å with the following columns:

- `wavelength`: the wavelength in air, in angstroms
- `ew`: the equivalent width, in milliangstroms, when known
- `id`: the identification (e.g. `Fe I`, `Si I`, `CN`, `Atm` for telluric lines)
- `flag`: `*` for lines HelioSpectrotron 5000 always labels, `-` for lines it never labels
