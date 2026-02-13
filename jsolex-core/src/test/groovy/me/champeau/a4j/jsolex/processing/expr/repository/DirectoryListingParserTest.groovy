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
package me.champeau.a4j.jsolex.processing.expr.repository

import spock.lang.Specification
import spock.lang.Subject

class DirectoryListingParserTest extends Specification {
    @Subject
    DirectoryListingParser parser = new DirectoryListingParser()

    def "parses HTML directory listing with math files"() {
        given:
        def html = '''
            <html><body>
            <a href="script1.math">script1.math</a>
            <a href="script2.math">script2.math</a>
            <a href="readme.txt">readme.txt</a>
            </body></html>
        '''

        when:
        def result = parser.parseHtmlListing(html)

        then:
        result == ['script1.math', 'script2.math']
    }

    def "parses HTML directory listing with zip files"() {
        given:
        def html = '''
            <html><body>
            <a href="script1.math">script1.math</a>
            <a href="bundle.zip">bundle.zip</a>
            </body></html>
        '''

        when:
        def result = parser.parseHtmlListing(html)

        then:
        result == ['script1.math', 'bundle.zip']
    }

    def "parses HTML directory listing with quoted attributes"() {
        given:
        def html = '''
            <html><body>
            <a href='script.math'>script.math</a>
            <a href="bundle.ZIP">bundle.ZIP</a>
            </body></html>
        '''

        when:
        def result = parser.parseHtmlListing(html)

        then:
        result == ['script.math', 'bundle.ZIP']
    }

    def "ignores paths with slashes"() {
        given:
        def html = '''
            <html><body>
            <a href="subdir/script.math">subdir/script.math</a>
            <a href="local.math">local.math</a>
            </body></html>
        '''

        when:
        def result = parser.parseHtmlListing(html)

        then:
        result == ['local.math']
    }

    def "parses scripts.txt file"() {
        given:
        def content = '''
            # This is a comment
            script1.math

            script2.math
            bundle.zip
            readme.txt
        '''

        when:
        def result = parser.parseScriptsTxt(content)

        then:
        result == ['script1.math', 'script2.math', 'bundle.zip']
    }

    def "parses scripts.txt with Windows line endings"() {
        given:
        def content = "script1.math\r\nscript2.math\r\nbundle.zip"

        when:
        def result = parser.parseScriptsTxt(content)

        then:
        result == ['script1.math', 'script2.math', 'bundle.zip']
    }
}
