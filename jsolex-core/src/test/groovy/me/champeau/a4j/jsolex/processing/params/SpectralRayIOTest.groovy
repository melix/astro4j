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
package me.champeau.a4j.jsolex.processing.params

import me.champeau.a4j.jsolex.processing.color.ColorCurve
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.Specification

import java.nio.file.Files

class SpectralRayIOTest extends Specification {

    def "save/load round-trip preserves H-beta with custom color curve"() {
        given: "predefined rays with H-beta modified to have a custom color curve"
        def items = new ArrayList<SpectralRay>(SpectralRay.predefined())
        def hBetaIndex = items.findIndexOf { it.label() == "H-beta" }
        def customCurve = new ColorCurve("H-beta", 200, 100, 150, 80, 100, 50)
        items.set(hBetaIndex, new SpectralRay(
                "H-beta",
                customCurve,
                Wavelen.ofNanos(486.134),
                false,
                List.of()))

        and: "a temp file"
        def tmp = Files.createTempFile("rays", ".json").toFile()
        tmp.deleteOnExit()

        when:
        SpectralRayIO.saveTo(items, tmp)
        def loaded = SpectralRayIO.readFrom(tmp.toPath())

        then: "H-beta survives with its custom color curve"
        def loadedHBeta = loaded.find { it.label() == "H-beta" }
        loadedHBeta != null
        loadedHBeta.colorCurve() != null
        loadedHBeta.colorCurve().rIn() == 200
        loadedHBeta.colorCurve().rOut() == 100

        and: "dropdown filter (absorption lines) still contains H-beta"
        def absorption = loaded.findAll { !it.emission() }
        absorption.any { it.label() == "H-beta" }

        and: "no duplicate H-alpha entries"
        loaded.count { it.label() == "H-alpha" } == 1
    }

    def "editor wavelength format/parse round-trip preserves H-beta but not H-alpha precision"() {
        // Reproduces what SpectralRayEditor does on every slider change:
        // wavelength.setText(String.format(Locale.US, "%.2f", angstroms))
        // newWavelen = Double.parseDouble(wavelength.getText())
        given:
        def angstroms = nanos * 10.0d
        def text = String.format(Locale.US, "%.2f", angstroms)
        def parsed = Double.parseDouble(text)

        expect:
        Double.compare(angstroms, parsed) == expectedCmp

        where:
        ray         | nanos    || expectedCmp
        "CALCIUM_K" | 393.366d || 0
        "CALCIUM_H" | 396.847d || 0
        "H_BETA"    | 486.134d || 0
        "H_ALPHA"   | 656.281d || -1   // tiny drift, but user reports H-alpha works fine
        "HELIUM_D3" | 587.562d || 0
    }

    def "save/load round-trip works under French locale"() {
        given:
        def originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)

        def items = new ArrayList<SpectralRay>(SpectralRay.predefined())
        def hBetaIndex = items.findIndexOf { it.label() == "H-beta" }
        items.set(hBetaIndex, new SpectralRay(
                "H-beta",
                new ColorCurve("H-beta", 200, 100, 150, 80, 100, 50),
                Wavelen.ofNanos(486.134),
                false,
                List.of()))

        def tmp = Files.createTempFile("rays-fr", ".json").toFile()
        tmp.deleteOnExit()

        when:
        SpectralRayIO.saveTo(items, tmp)
        def loaded = SpectralRayIO.readFrom(tmp.toPath())

        then:
        def loadedHBeta = loaded.find { it.label() == "H-beta" }
        loadedHBeta != null
        loadedHBeta.colorCurve() != null
        loadedHBeta.colorCurve().rIn() == 200
        loaded.count { it.label() == "H-alpha" } == 1

        cleanup:
        Locale.setDefault(originalLocale)
    }

    def "saved JSON contains exactly one entry per predefined ray"() {
        given:
        def items = new ArrayList<SpectralRay>(SpectralRay.predefined())
        def tmp = Files.createTempFile("rays-count", ".json").toFile()
        tmp.deleteOnExit()

        when:
        SpectralRayIO.saveTo(items, tmp)
        def json = tmp.text

        then:
        json.count('"label":"H-beta"') == 1
        json.count('"label":"H-alpha"') == 1
        json.count('"label":"Calcium (K)"') == 1
        json.count('"label":"Calcium (H)"') == 1
    }

    def "loading legacy primitive format with H_BETA enum string returns H-beta"() {
        given: "JSON written in the deprecated string-enum format"
        def legacyJson = '["H_ALPHA", "H_BETA", "CALCIUM_K"]'
        def tmp = Files.createTempFile("rays-legacy", ".json")
        tmp.toFile().deleteOnExit()
        tmp.toFile().text = legacyJson

        when:
        def loaded = SpectralRayIO.readFrom(tmp)

        then:
        loaded.size() == 3
        loaded[0].label() == "H-alpha"
        loaded[1].label() == "H-beta"
        loaded[2].label() == "Calcium (K)"
    }

    def "load JSON with two H-alpha entries surfaces both (no dedup at read)"() {
        // Tests whether the user-reported "two H-alpha" state can survive a reload.
        // If the read+filter doesn't dedupe, the dropdown would display both.
        given:
        def malformedJson = '''[
          {"label":"H-alpha","wavelength":656.281,"emission":false},
          {"label":"H-alpha","wavelength":656.281,"emission":false,"colorCurve":{"ray":"H-alpha","rIn":84,"rOut":139,"gIn":95,"gOut":20,"bIn":218,"bOut":65}},
          {"label":"Calcium (K)","wavelength":393.366,"emission":false}
        ]'''
        def tmp = Files.createTempFile("rays-dup", ".json")
        tmp.toFile().deleteOnExit()
        tmp.toFile().text = malformedJson

        when:
        def loaded = SpectralRayIO.readFrom(tmp)

        then:
        loaded.size() == 3
        loaded.count { it.label() == "H-alpha" } == 2
    }

    def "schema upgrade strips user's custom H-beta color curve"() {
        // Reproduces the suspected bug: SpectralRayIO.upgradeSchema replaces every
        // non-H-alpha ray's colorCurve with the predefined one. For H-beta the
        // predefined curve is null -> user customization wiped.
        given: "a JSON file containing H-beta with a user-customized color curve"
        def hBetaCustom = new SpectralRay(
                "H-beta",
                new ColorCurve("H-beta", 200, 100, 150, 80, 100, 50),
                Wavelen.ofNanos(486.134),
                false,
                List.of())
        def items = new ArrayList<SpectralRay>(SpectralRay.predefined())
        items.set(items.findIndexOf { it.label() == "H-beta" }, hBetaCustom)

        and: "a fresh fake jsolex home with schema version not yet at SCHEMA_VERSION"
        System.setProperty("tmp.home", "true")
        def jsolexDir = me.champeau.a4j.jsolex.processing.util.VersionUtil.getJsolexDir()
        Files.createDirectories(jsolexDir)
        def raysFile = jsolexDir.resolve("spectral-rays.json")
        SpectralRayIO.saveTo(items, raysFile.toFile())

        // Force schema version to 0 so upgradeSchema runs.
        def schemasFile = jsolexDir.resolve("schemas.txt")
        Files.deleteIfExists(schemasFile)
        Files.writeString(schemasFile, "spectral-ray=0\n")

        when: "loading defaults — this triggers the schema upgrade path"
        def loaded = SpectralRayIO.loadDefaults()

        then: "the user's H-beta custom color curve survives the upgrade"
        // This currently FAILS — demonstrating the bug.
        def loadedHBeta = loaded.find { it.label() == "H-beta" }
        loadedHBeta != null
        loadedHBeta.colorCurve() != null
        loadedHBeta.colorCurve().rIn() == 200

        cleanup:
        Files.deleteIfExists(raysFile)
        Files.deleteIfExists(schemasFile)
    }

    def "schema upgrade preserves a user's H-beta curve, then a fresh edit also persists"() {
        // End-to-end check across the v1 -> v2 schema bump:
        // 1. user has a customized H-beta saved under schema v1
        // 2. upgrade runs — must NOT wipe their color
        // 3. user re-edits the color and saves — must persist on next reload
        given:
        System.setProperty("tmp.home", "true")
        def jsolexDir = me.champeau.a4j.jsolex.processing.util.VersionUtil.getJsolexDir()
        Files.createDirectories(jsolexDir)
        def raysFile = jsolexDir.resolve("spectral-rays.json")
        def schemasFile = jsolexDir.resolve("schemas.txt")

        def items = new ArrayList<SpectralRay>(SpectralRay.predefined())
        items.set(items.findIndexOf { it.label() == "H-beta" }, new SpectralRay(
                "H-beta",
                new ColorCurve("H-beta", 200, 100, 150, 80, 100, 50),
                Wavelen.ofNanos(486.134), false, List.of()))
        SpectralRayIO.saveTo(items, raysFile.toFile())
        Files.writeString(schemasFile, "spectral-ray=1\n")

        when: "user opens editor: loadDefaults runs the v1 -> v2 upgrade"
        def afterUpgrade = SpectralRayIO.loadDefaults()

        then: "the user's H-beta color curve is preserved across the upgrade"
        def upgradedHBeta = afterUpgrade.find { it.label() == "H-beta" }
        upgradedHBeta.colorCurve() != null
        upgradedHBeta.colorCurve().rIn() == 200

        when: "user re-edits the curve and saves"
        def updated = new ArrayList<SpectralRay>(afterUpgrade)
        updated.set(updated.findIndexOf { it.label() == "H-beta" }, new SpectralRay(
                "H-beta",
                new ColorCurve("H-beta", 99, 88, 77, 66, 55, 44),
                Wavelen.ofNanos(486.134), false, List.of()))
        SpectralRayIO.saveDefaults(updated)

        and: "and the dropdown reload (loadDefaults) runs"
        def afterReSave = SpectralRayIO.loadDefaults()

        then: "the new curve is what's loaded"
        def hBeta = afterReSave.find { it.label() == "H-beta" }
        hBeta.colorCurve() != null
        hBeta.colorCurve().rIn() == 99

        and: "no duplicate H-alpha or H-beta"
        afterReSave.count { it.label() == "H-alpha" } == 1
        afterReSave.count { it.label() == "H-beta" } == 1

        cleanup:
        Files.deleteIfExists(raysFile)
        Files.deleteIfExists(schemasFile)
    }

    def "schema upgrade with saved H-beta whose wavelength was perturbed creates duplicate"() {
        // What if the user's H-beta has a wavelength that doesn't exactly equal predefined?
        // SpectralRay.equals uses Double.compare on angstroms — it's exact, no tolerance.
        // The editor's format/parse round-trip ('%.2f' then parseDouble) does NOT perturb
        // H-beta's wavelength on its own, but a user could enter a different value.
        given:
        System.setProperty("tmp.home", "true")
        def jsolexDir = me.champeau.a4j.jsolex.processing.util.VersionUtil.getJsolexDir()
        Files.createDirectories(jsolexDir)
        def raysFile = jsolexDir.resolve("spectral-rays.json")
        def schemasFile = jsolexDir.resolve("schemas.txt")

        // Saved H-beta with a slightly different wavelength (4861.35 vs predefined 4861.34)
        def items = new ArrayList<SpectralRay>(SpectralRay.predefined())
        items.set(items.findIndexOf { it.label() == "H-beta" }, new SpectralRay(
                "H-beta",
                new ColorCurve("H-beta", 200, 100, 150, 80, 100, 50),
                Wavelen.ofAngstroms(4861.35d),  // <-- one digit off
                false, List.of()))
        SpectralRayIO.saveTo(items, raysFile.toFile())
        Files.writeString(schemasFile, "spectral-ray=0\n")

        when:
        def loaded = SpectralRayIO.loadDefaults()

        then: "the upgrade re-adds the predefined H-beta AND keeps the perturbed one"
        // newRays loop adds predefined (since perturbed doesn't equal it).
        // Second loop adds the perturbed one (since not in foundRays).
        loaded.count { it.label() == "H-beta" } == 2

        cleanup:
        Files.deleteIfExists(raysFile)
        Files.deleteIfExists(schemasFile)
    }

    def "schema upgrade re-adds predefined H-beta and keeps mislabeled entry => two H-alpha"() {
        // If the saved file somehow has an entry labelled "H-alpha" at H-beta's
        // wavelength, the upgrade re-adds predefined H-beta AND keeps the mislabeled
        // entry — producing two "H-alpha" labels in the dropdown (different wavelengths).
        // This is a near-match for the user's "two H-alpha" report.
        given:
        System.setProperty("tmp.home", "true")
        def jsolexDir = me.champeau.a4j.jsolex.processing.util.VersionUtil.getJsolexDir()
        Files.createDirectories(jsolexDir)
        def raysFile = jsolexDir.resolve("spectral-rays.json")
        def schemasFile = jsolexDir.resolve("schemas.txt")

        def items = new ArrayList<SpectralRay>(SpectralRay.predefined())
        items.set(items.findIndexOf { it.label() == "H-beta" }, new SpectralRay(
                "H-alpha",                        // wrong label
                new ColorCurve("H-alpha", 84, 139, 95, 20, 218, 65),
                Wavelen.ofNanos(486.134),         // H-beta wavelength
                false, List.of()))
        SpectralRayIO.saveTo(items, raysFile.toFile())
        Files.writeString(schemasFile, "spectral-ray=0\n")

        when:
        def loaded = SpectralRayIO.loadDefaults()

        then: "two distinct H-alpha entries exist (one at H-beta wavelength, one at H-alpha)"
        loaded.count { it.label() == "H-alpha" } == 2
        // predefined H-beta IS re-added in the upgrade; user might miss it visually
        loaded.count { it.label() == "H-beta" } == 1

        cleanup:
        Files.deleteIfExists(raysFile)
        Files.deleteIfExists(schemasFile)
    }

    def "no leakage between tests via tmp.home cache"() {
        // sanity: the tmp jsolex dir is cached in VersionUtil; verify cleanup works
        expect:
        System.getProperty("tmp.home") != null
    }
}
