/*
 * Copyright 2025 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.util;

public class HardwareDatabaseUtils {
    public static Vendor normalizeVendor(String name) {
        if (name == null) {
            return new Vendor("Unknown", "UNK");
        }
        var norm = name.toLowerCase()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
        return switch (norm) {
            case "acuter" -> new Vendor("Acuter", "ACU");
            case "altair" -> new Vendor("Altair", "ALT");
            case "artesky" -> new Vendor("Artesky", "ART");
            case "askar" -> new Vendor("Askar", "ASK");
            case "borg" -> new Vendor("Borg", "BOR");
            case "canon" -> new Vendor("Canon", "CAN");
            case "celestron" -> new Vendor("Celestron", "CEL");
            case "coronado" -> new Vendor("Coronado", "COR");
            case "daystar" -> new Vendor("DayStar", "DAY");
            case "meade" -> new Vendor("Meade", "MEA");
            case "moonraker" -> new Vendor("Moonraker", "MOO");
            case "nikon" -> new Vendor("Nikon", "NIK");
            case "orion" -> new Vendor("Orion", "ORI");
            case "sharpstar" -> new Vendor("SharpStar", "SHA");
            case "skywatcher" -> new Vendor("SkyWatcher", "SKY");
            case "svbony" -> new Vendor("Svbony", "SVB");
            case "takahashi" -> new Vendor("Takahashi", "TAK");
            case "tamron" -> new Vendor("Tamron", "TAM");
            case "tsoptics" -> new Vendor("TS-Optics", "TS");
            case "williamoptic", "williamoptics" -> new Vendor("William Optics", "WO");
            case "10micron" -> new Vendor("10micron", "10M");
            case "ioptron" -> new Vendor("iOptron", "IOP");
            case "astrophysics" -> new Vendor("Astro-Physics", "AST");
            case "mesuoptics" -> new Vendor("Mesu-Optics", "MES");
            case "omegon" -> new Vendor("Omegon", "OME");
            case "paramount" -> new Vendor("Paramount", "PAR");
            case "promaker" -> new Vendor("ProMaker", "PRO");
            case "teseek" -> new Vendor("Teseek", "TES");
            case "vixen" -> new Vendor("Vixen", "VIX");
            case "warpastron" -> new Vendor("WarpAstron", "WAR");
            case "zwo" -> new Vendor("ZWO", "ZWO");
            case "qhyccd", "qhy" -> new Vendor("QHY", "QHY");
            case "pegasusastro" -> new Vendor("Pegasus Astro", "PEG");
            case "mlastro" -> new Vendor("MLAstro", "MLA");
            case "playerone" -> new Vendor("Player One", "PLA");
            case "touptek" -> new Vendor("ToupTek", "TOU");
            default -> new Vendor(name.trim(), name.substring(0, Math.min(3, name.length())).toUpperCase());
        };
    }

    public static String cameraToSensor(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        var norm = model.toLowerCase().replace(" ", "").replace("-", "").replace("_", "");

        // Player One cameras (named after planets)
        if (norm.contains("uranusm") || norm.contains("uranusc")) {
            return "IMX585";
        }
        if (norm.contains("neptunecii") || norm.contains("neptunemii")) {
            return "IMX464";
        }
        if (norm.contains("neptunec") || norm.contains("neptunem")) {
            return "IMX178";
        }
        if (norm.contains("marscii") || norm.contains("marsmii")) {
            return "IMX662";
        }
        if (norm.contains("marsc") || norm.contains("marsm")) {
            return "IMX462";
        }
        if (norm.contains("apollo") && norm.contains("428")) {
            return "IMX428";
        }
        if (norm.contains("apollo") && norm.contains("mini")) {
            return "IMX429";
        }
        if (norm.contains("apollo") && norm.contains("max")) {
            return "IMX432";
        }
        if (norm.contains("apollom") || norm.contains("apolloc")) {
            return "IMX174";
        }

        // SVBony cameras
        if (norm.contains("sv705")) {
            return "IMX585";
        }
        if (norm.contains("sv605")) {
            return "IMX533";
        }
        if (norm.contains("sv305")) {
            return "IMX290";
        }

        // Match by sensor number pattern (covers ZWO ASI, QHY, ToupTek, etc.)
        if (norm.contains("6200")) {
            return "IMX455";
        }
        if (norm.contains("2600")) {
            return "IMX571";
        }
        if (norm.contains("1600")) {
            return "MN34230";
        }
        if (norm.contains("678")) {
            return "IMX678";
        }
        if (norm.contains("664")) {
            return "IMX664";
        }
        if (norm.contains("662")) {
            return "IMX662";
        }
        if (norm.contains("600") && !norm.contains("6200") && !norm.contains("2600") && !norm.contains("1600")) {
            return "IMX455";
        }
        if (norm.contains("585")) {
            return "IMX585";
        }
        if (norm.contains("571")) {
            return "IMX571";
        }
        if (norm.contains("533")) {
            return "IMX533";
        }
        if (norm.contains("492")) {
            return "IMX492";
        }
        if (norm.contains("485")) {
            return "IMX485";
        }
        if (norm.contains("482")) {
            return "IMX482";
        }
        if (norm.contains("464")) {
            return "IMX464";
        }
        if (norm.contains("462")) {
            return "IMX462";
        }
        if (norm.contains("455")) {
            return "IMX455";
        }
        if (norm.contains("385")) {
            return "IMX385";
        }
        if (norm.contains("294")) {
            return "IMX294";
        }
        if (norm.contains("290")) {
            return "IMX290";
        }
        if (norm.contains("268")) {
            return "IMX571";
        }
        if (norm.contains("249")) {
            return "IMX249";
        }
        if (norm.contains("224")) {
            return "IMX224";
        }
        if (norm.contains("183")) {
            return "IMX183";
        }
        if (norm.contains("178")) {
            return "IMX178";
        }
        if (norm.contains("174")) {
            return "IMX174";
        }
        if (norm.contains("120")) {
            return "AR0130";
        }

        return null;
    }

    public static Double sensorToPixelSizeMicrons(String sensor) {
        if (sensor == null) {
            return null;
        }
        return switch (sensor.toUpperCase()) {
            case "IMX178", "IMX183" -> 2.4;
            case "IMX678" -> 2.0;
            case "IMX290", "IMX462", "IMX464", "IMX485", "IMX585", "IMX662", "IMX664" -> 2.9;
            case "IMX224", "IMX385", "AR0130" -> 3.75;
            case "IMX455", "IMX533", "IMX571" -> 3.76;
            case "MN34230" -> 3.8;
            case "IMX428", "IMX429" -> 4.5;
            case "IMX294", "IMX492" -> 4.63;
            case "IMX482" -> 5.8;
            case "IMX174", "IMX249" -> 5.86;
            case "IMX432" -> 9.0;
            default -> null;
        };
    }

    public static Double cameraToPixelSizeMicrons(String model) {
        var sensor = cameraToSensor(model);
        if (sensor != null) {
            return sensorToPixelSizeMicrons(sensor);
        }
        return null;
    }

    public record Vendor(String normalizedName, String code) {

    }
}
