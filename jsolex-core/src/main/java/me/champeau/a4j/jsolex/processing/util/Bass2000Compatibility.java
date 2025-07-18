package me.champeau.a4j.jsolex.processing.util;

/**
 * This class is used specifically to ensure compatibility with the BASS2000
 * database when saving FITS files.
 */
public record Bass2000Compatibility(
        double forcedWavelengthAngstroms
) {
}
