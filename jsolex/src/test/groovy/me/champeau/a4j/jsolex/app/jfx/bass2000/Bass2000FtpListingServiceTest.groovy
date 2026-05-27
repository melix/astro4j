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
package me.champeau.a4j.jsolex.app.jfx.bass2000

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
import java.time.LocalDateTime

class Bass2000FtpListingServiceTest extends Specification {

    @Unroll
    def "parses #filename"() {
        when:
        def result = Bass2000FtpListingService.parseFilename(filename)

        then:
        result.isPresent()
        result.get().lineCode() == expectedLineCode
        result.get().captureTime() == expectedCapture
        result.get().filename() == expectedBaseName

        where:
        filename                                                                           | expectedLineCode | expectedCapture                                | expectedBaseName
        '_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_20251201_103000'      | 'Ha'             | LocalDateTime.of(2025, 12, 1, 10, 30, 0)       | '_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_20251201_103000'
        '_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_20251201_103000.fits' | 'Ha'             | LocalDateTime.of(2025, 12, 1, 10, 30, 0)       | '_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_20251201_103000.fits'
        '_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_20251201_103000.fit'  | 'Ha'             | LocalDateTime.of(2025, 12, 1, 10, 30, 0)       | '_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_20251201_103000.fit'
        '_10_30_00Z_dp-2_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha2cb_20251201_103000.fits' | 'Ha2cb' | LocalDateTime.of(2025, 12, 1, 10, 30, 0)       | '_10_30_00Z_dp-2_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha2cb_20251201_103000.fits'
        '_10_30_00Z_dp3_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Cak1v_20240115_073015.fits' | 'Cak1v'   | LocalDateTime.of(2024, 1, 15, 7, 30, 15)       | '_10_30_00Z_dp3_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Cak1v_20240115_073015.fits'
        '_06_45_22Z_SHG700_120_800_O_TS-PHOTOLINE_QHY-QHY5III174M_IOPTRON-CEM26_Cak_20240520_064522.fits' | 'Cak' | LocalDateTime.of(2024, 5, 20, 6, 45, 22) | '_06_45_22Z_SHG700_120_800_O_TS-PHOTOLINE_QHY-QHY5III174M_IOPTRON-CEM26_Cak_20240520_064522.fits'
        '_06_45_22Z_SHG700_120_800_O_TS-PHOTOLINE_QHY-QHY5III174M_IOPTRON-CEM26_Cah_20240520_064522.fits' | 'Cah' | LocalDateTime.of(2024, 5, 20, 6, 45, 22) | '_06_45_22Z_SHG700_120_800_O_TS-PHOTOLINE_QHY-QHY5III174M_IOPTRON-CEM26_Cah_20240520_064522.fits'
        'incoming/solap/_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_20251201_103000.fits' | 'Ha' | LocalDateTime.of(2025, 12, 1, 10, 30, 0) | '_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_20251201_103000.fits'
    }

    @Unroll
    def "rejects malformed filename #filename"() {
        expect:
        Bass2000FtpListingService.parseFilename(filename).isEmpty()

        where:
        filename << [
                null,
                '',
                'README.txt',
                'random-file.fits',
                '_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Xx_20251201_103000.fits',
                '_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_2025120_103000.fits',
                '_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_20251201_10300.fits',
                'no_leading_underscore_Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_20251201_103000',
                '_10_30_00Z_SOLEX_90_1000_N_SW-ED80_ZWO-ASI178MM_SW-AZEQ6_Ha_99991301_103000.fits'
        ]
    }

    @Unroll
    def "groups #parsed and #requested as matching=#expected"() {
        expect:
        Bass2000FtpListingService.matchesLine(parsed, requested) == expected

        where:
        parsed  | requested | expected
        'Ha'    | 'Ha'      | true
        'Ha2cb' | 'Ha'      | true
        'Ha'    | 'Ha2cb'   | true
        'Cak'   | 'Cak'     | true
        'Cak1v' | 'Cak'     | true
        'Cah'   | 'Cah'     | true
        'Cah1v' | 'Cah'     | true
        'Ha'    | 'Cak'     | false
        'Cak'   | 'Cah'     | false
        'Ha2cb' | 'Cak1v'   | false
        'ha'    | 'HA'      | true
        'Ha'    | null      | true
    }

    def "filterSubmissions returns empty when names array is null"() {
        expect:
        Bass2000FtpListingService.filterSubmissions(null, LocalDate.of(2024, 5, 20), 'Ha').isEmpty()
    }

    def "filterSubmissions keeps only entries matching date and line group"() {
        given:
        def names = [
                '_06_45_22Z_SHG700_120_800_O_TS-PL_QHY-QHY5III_IOPTRON-CEM26_Ha_20240520_064522.fits',
                '_07_10_05Z_SHG700_120_800_O_TS-PL_QHY-QHY5III_IOPTRON-CEM26_Ha2cb_20240520_071005.fits',
                '_07_30_00Z_SHG700_120_800_O_TS-PL_QHY-QHY5III_IOPTRON-CEM26_Cak_20240520_073000.fits',
                '_08_00_00Z_SHG700_120_800_O_TS-PL_QHY-QHY5III_IOPTRON-CEM26_Ha_20240521_080000.fits',
                'README.txt',
                null
        ] as String[]

        when:
        def haMatches = Bass2000FtpListingService.filterSubmissions(names, LocalDate.of(2024, 5, 20), 'Ha')

        then:
        haMatches.size() == 2
        haMatches*.lineCode().sort() == ['Ha', 'Ha2cb']
        haMatches.every { it.captureTime().toLocalDate() == LocalDate.of(2024, 5, 20) }

        when:
        def cakMatches = Bass2000FtpListingService.filterSubmissions(names, LocalDate.of(2024, 5, 20), 'Cak')

        then:
        cakMatches.size() == 1
        cakMatches[0].lineCode() == 'Cak'

        when:
        def noMatches = Bass2000FtpListingService.filterSubmissions(names, LocalDate.of(2024, 5, 22), 'Ha')

        then:
        noMatches.isEmpty()
    }

    def "RemoteSubmission delta is symmetric and absolute"() {
        given:
        def submission = new Bass2000FtpListingService.RemoteSubmission(
                'file.fits',
                LocalDateTime.of(2024, 5, 20, 10, 0, 0),
                'Ha'
        )

        expect:
        submission.timeDeltaTo(LocalDateTime.of(2024, 5, 20, 10, 5, 0)).toMinutes() == 5
        submission.timeDeltaTo(LocalDateTime.of(2024, 5, 20, 9, 55, 0)).toMinutes() == 5
        submission.timeDeltaTo(LocalDateTime.of(2024, 5, 20, 10, 0, 0)).isZero()
    }
}
