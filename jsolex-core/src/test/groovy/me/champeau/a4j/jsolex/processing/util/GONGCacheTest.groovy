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
package me.champeau.a4j.jsolex.processing.util

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime

class GONGCacheTest extends Specification {

    @TempDir
    Path tempDir

    private static final ZonedDateTime TIMESTAMP = ZonedDateTime.of(2026, 6, 1, 0, 0, 2, 0, ZoneId.of('UTC'))

    private Path cacheFolder() {
        tempDir.resolve('gong')
    }

    private GONG.GongCandidate candidate(Path source, GONG.GongResolution resolution) {
        new GONG.GongCandidate(source.toUri().toURL(), TIMESTAMP, 'A', '20260601000002Ah.jpg', resolution)
    }

    private static Path sourceFile(Path dir, String name, byte[] content) {
        var file = dir.resolve(name)
        Files.write(file, content)
        file
    }

    private static byte[] readResult(Optional<URL> result) {
        Files.readAllBytes(Path.of(result.get().toURI()))
    }

    def "first fetch downloads the image into the cache folder"() {
        given:
        var source = sourceFile(tempDir, 'source.jpg', 'original'.bytes)
        var candidate = candidate(source, GONG.GongResolution.LOW)

        when:
        var result = GONG.fetchCandidateImage(candidate, cacheFolder())

        then:
        result.present
        var cached = Path.of(result.get().toURI())
        cached.startsWith(cacheFolder())
        cached.endsWith('20260601000002Ah.jpg')
        readResult(result) == 'original'.bytes
    }

    def "second fetch returns the cached image without re-downloading"() {
        given: 'a first fetch that populates the cache'
        var source = sourceFile(tempDir, 'source.jpg', 'original'.bytes)
        var candidate = candidate(source, GONG.GongResolution.LOW)
        var first = GONG.fetchCandidateImage(candidate, cacheFolder())

        and: 'the remote source is changed afterwards'
        Files.write(source, 'modified'.bytes)

        when: 'the same candidate is fetched again'
        var second = GONG.fetchCandidateImage(candidate, cacheFolder())

        then: 'the original cached bytes are returned, proving no re-download happened'
        second.present
        Path.of(second.get().toURI()) == Path.of(first.get().toURI())
        readResult(second) == 'original'.bytes
    }

    def "a cached listing for a past day is read from disk without hitting the network"() {
        given: 'a listing cached on disk for a day in the past'
        var pastDay = ZonedDateTime.of(2020, 1, 1, 12, 0, 0, 0, ZoneId.of('UTC'))
        var listingDir = cacheFolder().resolve('HA/hav/202001/20200101')
        Files.createDirectories(listingDir)
        Files.write(listingDir.resolve('listing.txt'), ['20200101120002Ah.jpg', '20200101120502Bh.jpg'])

        when: 'candidates are listed (offline: any network access would fail)'
        var candidates = GONG.listGongCandidates(pastDay, GONG.GongResolution.LOW, cacheFolder())

        then: 'the cached listing is parsed and sorted by proximity to the requested time'
        candidates*.fileName() == ['20200101120002Ah.jpg', '20200101120502Bh.jpg']
        candidates*.siteCode() == ['A', 'B']
        candidates*.resolution() == [GONG.GongResolution.LOW, GONG.GongResolution.LOW]
        candidates.first().url().toExternalForm().endsWith('/HA/hav/202001/20200101/20200101120002Ah.jpg')
    }

    def "different resolutions are cached independently and never collide"() {
        given: 'two sources with identical file names but different resolutions/content'
        var lowSource = sourceFile(tempDir, 'low.jpg', 'low-res'.bytes)
        var fullSource = sourceFile(tempDir, 'full.jpg', 'full-res'.bytes)

        when:
        var low = GONG.fetchCandidateImage(candidate(lowSource, GONG.GongResolution.LOW), cacheFolder())
        var full = GONG.fetchCandidateImage(candidate(fullSource, GONG.GongResolution.FULL), cacheFolder())

        then: 'they map to distinct cache locations with the expected content'
        Path.of(low.get().toURI()) != Path.of(full.get().toURI())
        readResult(low) == 'low-res'.bytes
        readResult(full) == 'full-res'.bytes

        and: 'the resolution directory is part of the cache path'
        low.get().toExternalForm().contains('/HA/hav/')
        full.get().toExternalForm().contains('/HA/hag/')
    }
}
