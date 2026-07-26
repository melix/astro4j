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

class RemoteImageCacheTest extends Specification {

    @TempDir
    Path tempDir

    private static final String LISTING = '''
        <html><body>
        <a href="20260725_001500_1024_0094.jpg">first</a>
        <a href="20260725_002400_1024_0094.jpg">second</a>
        <a href="listing.txt">not an image</a>
        </body></html>
    '''

    private URL listingUrl(String content) {
        var file = Files.createTempFile(tempDir, 'listing', '.html')
        Files.writeString(file, content)
        file.toUri().toURL()
    }

    private Path cacheFile() {
        tempDir.resolve('cache').resolve('listing.txt')
    }

    def "only keeps the jpg links of a directory listing"() {
        when:
        var links = RemoteImageCache.listJpgLinks(listingUrl(LISTING), cacheFile(), false)

        then:
        links == ['20260725_001500_1024_0094.jpg', '20260725_002400_1024_0094.jpg']
    }

    def "an immutable listing is cached on disk and reused"() {
        given:
        var first = listingUrl(LISTING)

        when:
        var initial = RemoteImageCache.listJpgLinks(first, cacheFile(), true)

        then:
        initial.size() == 2
        Files.exists(cacheFile())

        when: "the server publishes a different listing"
        var updated = listingUrl('<a href="something_else.jpg">x</a>')
        var second = RemoteImageCache.listJpgLinks(updated, cacheFile(), true)

        then: "the cached listing is returned instead"
        second == initial
    }

    def "a listing which can still change is never cached"() {
        given:
        var first = listingUrl(LISTING)

        when:
        var initial = RemoteImageCache.listJpgLinks(first, cacheFile(), false)

        then:
        initial.size() == 2
        !Files.exists(cacheFile())

        when:
        var updated = listingUrl('<a href="something_else.jpg">x</a>')
        var second = RemoteImageCache.listJpgLinks(updated, cacheFile(), false)

        then:
        second == ['something_else.jpg']
    }

    def "an empty listing is not cached, so that it can be fetched again"() {
        when:
        var links = RemoteImageCache.listJpgLinks(listingUrl('<html></html>'), cacheFile(), true)

        then:
        links.isEmpty()
        !Files.exists(cacheFile())
    }

    def "an image is downloaded once then served from the cache"() {
        given:
        var source = Files.writeString(tempDir.resolve('source.jpg'), 'original')
        var target = tempDir.resolve('cache').resolve('image.jpg')

        when:
        var first = RemoteImageCache.fetchImage(source.toUri().toURL(), target)

        then:
        first.present
        Files.readString(Path.of(first.get().toURI())) == 'original'

        when: "the remote image changes but the cached copy is still present"
        Files.writeString(source, 'updated')
        var second = RemoteImageCache.fetchImage(source.toUri().toURL(), target)

        then: "the cached copy is returned"
        Files.readString(Path.of(second.get().toURI())) == 'original'
    }

    def "the direct url is returned when the image cannot be cached"() {
        given:
        var source = Files.writeString(tempDir.resolve('source.jpg'), 'original')
        var blocker = Files.writeString(tempDir.resolve('blocker'), 'not a directory')
        var target = blocker.resolve('image.jpg')

        when:
        var result = RemoteImageCache.fetchImage(source.toUri().toURL(), target)

        then:
        result.present
        result.get() == source.toUri().toURL()
    }

    def "yesterday is a past day but today is not"() {
        given:
        var now = ZonedDateTime.now(ZoneId.of('UTC'))

        expect:
        RemoteImageCache.isPastUtcDay(now.minusDays(1))
        !RemoteImageCache.isPastUtcDay(now)
        !RemoteImageCache.isPastUtcDay(now.plusDays(1))
    }
}
