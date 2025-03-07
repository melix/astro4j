package me.champeau.a4j.jsolex.expr

import me.champeau.a4j.expr.ImageMathParser
import spock.lang.Specification

class ImageMathParserTest extends Specification {
    def "parses valid scripts"() {
        def parser = new ImageMathParser(script)
        when:
        parser.Root()
        def root = parser.rootNode()

        then:
        root.dump()
        and:
        {
            root.descendantsOfType(me.champeau.a4j.expr.ast.Section).each {
                println "Section ${it.name()}"
            }

            root.descendantsOfType(me.champeau.a4j.expr.ast.FunctionCall).each {
                println "Fun call: ${it.functionName}"
                println "Fun args: ${it.arguments}"
            }
        }

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
