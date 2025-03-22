package me.champeau.a4j.jsolex.expr


import spock.lang.Specification

import java.nio.file.Path

class ImageMathParserTest extends Specification {
    def "parses valid scripts"() {
        def parser = new ImageMathParser(script)
        parser.includeDir = Path.of("src/test/resources/me/champeau/a4j/jsolex/expr")

        when:
        def root = parser.parseAndInlineIncludes()

        then:
        root.dump()

        where:
        script << buildSamples()
    }

    private static List<String> buildSamples() {
        int i = 0
        List<String> samples = []
        while (true) {
            var stream = ImageMathParserTest.getResourceAsStream("script${i++}.txt")
            if (stream == null) {
                break
            }
            samples << stream.text
        }
        samples
    }
}
