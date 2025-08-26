/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.math.Point2D;

/**
 * Utility class for calculating Doppler corrections due to solar rotation.
 * This class provides methods to compute position-dependent pixel shift corrections
 * based on the solar rotation velocity at different positions on the solar disk.
 */
public final class DopplerCorrection {
    
    // Solar rotation rate at equator in rad/s (sidereal rotation period ~25.38 days)
    private static final double SOLAR_ROTATION_RATE = 2.865e-6;
    
    // Solar radius in km
    private static final double SOLAR_RADIUS_KM = 695700.0;
    
    // Speed of light in km/s
    private static final double SPEED_OF_LIGHT_KM_S = 299792.458;
    
    private DopplerCorrection() {
        // Utility class
    }
    
    /**
     * Calculates the Doppler correction for a spatial position on the solar disk.
     * 
     * @param spatialX the spatial x-coordinate along the slit (corresponds to position across solar disk)
     * @param reconstructedY the y-coordinate in the reconstructed image (corresponds to scanning position)
     * @param ellipse the fitted solar disk ellipse
     * @param solarParams the solar orientation parameters
     * @param observationWavelength the observation wavelength in Angstroms
     * @param dispersionPerPixel the spectral dispersion in Angstroms per pixel
     * @return the pixel shift correction to apply (positive for redshift, negative for blueshift)
     */
    public static double calculatePixelShiftCorrection(double spatialX, 
                                                       double reconstructedY,
                                                       Ellipse ellipse,
                                                       SolarParameters solarParams,
                                                       double observationWavelength,
                                                       double dispersionPerPixel) {
        // Convert spatial coordinates to solar disk coordinates
        var diskCoords = pixelToSolarDisk(spatialX, reconstructedY, ellipse);
        
        // Check if pixel is within the solar disk
        if (Math.sqrt(diskCoords.x() * diskCoords.x() + diskCoords.y() * diskCoords.y()) > 1.0) {
            return 0.0; // No correction outside the disk
        }
        
        // Calculate solar rotation velocity at this position
        double rotationalVelocity = calculateRotationalVelocity(diskCoords, solarParams);
        
        // Convert velocity to wavelength shift
        double wavelengthShift = (rotationalVelocity / SPEED_OF_LIGHT_KM_S) * observationWavelength;
        
        // Convert wavelength shift to pixel shift correction
        return wavelengthShift / dispersionPerPixel;
    }
    
    /**
     * Converts reconstruction coordinates to normalized solar disk coordinates.
     * JSol'Ex coordinate system: East is on top (negative Y), North is on the right (positive X).
     * The solar disk ellipse is mapped to normalized coordinates where the disk is a unit circle.
     * 
     * @param reconstructionX the x-coordinate in the reconstructed image (spatial position along slit)
     * @param reconstructionY the y-coordinate in the reconstructed image (scanning position) 
     * @param ellipse the fitted solar disk ellipse
     * @return normalized coordinates on the solar disk (-1 to +1 range, with proper solar orientation)
     */
    private static Point2D pixelToSolarDisk(double reconstructionX, double reconstructionY, Ellipse ellipse) {
        var center = ellipse.center();
        var semiAxis = ellipse.semiAxis();
        
        // Translate to ellipse center
        double dx = reconstructionX - center.a();
        double dy = reconstructionY - center.b();
        
        // Handle rotation if ellipse is rotated
        double angle = ellipse.rotationAngle();
        if (Math.abs(angle) > 1e-6) {
            double cos = Math.cos(-angle);
            double sin = Math.sin(-angle);
            double rotatedX = dx * cos - dy * sin;
            double rotatedY = dx * sin + dy * cos;
            dx = rotatedX;
            dy = rotatedY;
        }
        
        // Normalize by semi-axes to get unit circle coordinates
        double normalizedX = dx / semiAxis.a();
        double normalizedY = dy / semiAxis.b();
        
        // Convert to solar coordinates: JSol'Ex has East on top, North on right
        // In solar coordinates: East = +X, North = +Y
        // In JSol'Ex coordinates: East = -Y direction, North = +X direction
        // So: solar_east = -reconstructionY_normalized, solar_north = +reconstructionX_normalized
        double solarEast = -normalizedY;  // East component (negative Y in JSol'Ex)
        double solarNorth = normalizedX;  // North component (positive X in JSol'Ex)
        
        return new Point2D(solarEast, solarNorth);
    }
    
    /**
     * Calculates the rotational velocity at a given position on the solar disk.
     * 
     * @param solarCoords solar coordinates (x=East, y=North) on the normalized disk
     * @param solarParams solar orientation parameters  
     * @return the line-of-sight rotational velocity in km/s (positive = receding/redshift)
     */
    private static double calculateRotationalVelocity(Point2D solarCoords, SolarParameters solarParams) {
        double eastCoord = solarCoords.x();   // East-West position on disk
        double northCoord = solarCoords.y();  // North-South position on disk
        
        // Calculate heliographic coordinates
        // Convert from disk coordinates to 3D sphere coordinates
        double rho = Math.sqrt(eastCoord * eastCoord + northCoord * northCoord);
        if (rho > 1.0) {
            return 0.0; // Outside disk
        }
        
        double z = Math.sqrt(1.0 - rho * rho); // Z-coordinate on unit sphere
        
        // Apply solar orientation corrections
        double b0 = solarParams.b0(); // Heliographic latitude of disk center
        double l0 = solarParams.l0(); // Carrington longitude of central meridian
        double p = solarParams.p();   // Position angle of solar north pole
        
        // Transform coordinates considering solar tilt (P angle)
        double cosP = Math.cos(p);
        double sinP = Math.sin(p);
        double xRot = eastCoord * cosP - northCoord * sinP;
        double yRot = eastCoord * sinP + northCoord * cosP;
        
        // Calculate heliographic latitude (phi) and longitude (lambda)
        double sinPhi = yRot * Math.cos(b0) + z * Math.sin(b0);
        double phi = Math.asin(Math.max(-1.0, Math.min(1.0, sinPhi)));
        
        double lambda = Math.atan2(xRot, z * Math.cos(b0) - yRot * Math.sin(b0)) + l0;
        
        // Solar differential rotation (Carrington rotation law)
        // Omega(latitude) = Omega0 * (1 - 0.189 * sin²φ - 0.222 * sin⁴φ)
        double sinPhiSq = sinPhi * sinPhi;
        double rotationRate = SOLAR_ROTATION_RATE * (1.0 - 0.189 * sinPhiSq - 0.222 * sinPhiSq * sinPhiSq);
        
        // Calculate rotational velocity
        double equatorialVelocity = rotationRate * SOLAR_RADIUS_KM * Math.cos(phi);
        
        // Project onto line of sight (east-west component visible as Doppler shift)
        // Positive velocity = moving away from observer (redshift)
        double lineOfSightVelocity = equatorialVelocity * Math.sin(lambda - l0) * Math.cos(phi);
        
        return lineOfSightVelocity;
    }
    
    /**
     * Estimates the spectral dispersion based on instrument parameters.
     * This is a fallback method when dispersion is not directly available.
     * 
     * @param instrumentType the type of instrument
     * @param binning the pixel binning factor
     * @return estimated dispersion in Angstroms per pixel
     */
    public static double estimateDispersion(String instrumentType, int binning) {
        // Default dispersions for common setups (rough estimates)
        double baseDispersion = switch (instrumentType.toLowerCase()) {
            case "solex" -> 0.006; // ~0.006 Å/pixel for Sol'Ex
            case "lhires" -> 0.01;  // ~0.01 Å/pixel for LHIRES III
            case "alpy" -> 0.33;    // ~0.33 Å/pixel for ALPY 600
            default -> 0.01;        // Generic estimate
        };
        
        return baseDispersion * binning;
    }
}