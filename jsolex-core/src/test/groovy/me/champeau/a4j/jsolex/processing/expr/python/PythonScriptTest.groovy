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
package me.champeau.a4j.jsolex.processing.expr.python

import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor.SectionKind
import me.champeau.a4j.jsolex.processing.expr.ShiftCollectingImageExpressionEvaluator
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords
import me.champeau.a4j.jsolex.processing.sun.workflow.SpectralLinePolynomial
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.SolarParameters
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleQuadruplet
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files

class PythonScriptTest extends Specification {

    @Subject
    PythonScriptExecutor executor

    ShiftCollectingImageExpressionEvaluator evaluator
    Map<Class<?>, Object> context

    def setup() {
        evaluator = new ShiftCollectingImageExpressionEvaluator(Broadcaster.NO_OP)
        context = new HashMap<>()
        executor = new PythonScriptExecutor(evaluator, context, Broadcaster.NO_OP)
    }

    // ==================== Basic Execution Tests ====================

    def "can execute simple arithmetic expression"() {
        when:
        def result = executor.executeInline("result = 2 + 3", [:])

        then:
        result == 5.0d
    }

    def "can execute multi-line Python script"() {
        when:
        def result = executor.executeInline('''
x = 10
y = 20
result = x * y
''', [:])

        then:
        result == 200.0d
    }

    def "can access variables passed to script"() {
        when:
        def result = executor.executeInline("result = a + b", [a: 5.0, b: 3.0])

        then:
        result == 8.0d
    }

    def "returns null when no result is set"() {
        when:
        def result = executor.executeInline("x = 42", [:])

        then:
        result == null
    }

    // ==================== Type Conversion Tests ====================

    def "can return string from Python"() {
        when:
        def result = executor.executeInline('result = "hello"', [:])

        then:
        result == "hello"
    }

    def "can return boolean from Python"() {
        when:
        def trueResult = executor.executeInline("result = True", [:])
        def falseResult = executor.executeInline("result = False", [:])

        then:
        trueResult == true
        falseResult == false
    }

    def "can return list from Python"() {
        when:
        def result = executor.executeInline("result = [1, 2, 3]", [:])

        then:
        result instanceof List
        result == [1.0d, 2.0d, 3.0d]
    }

    def "can return dict from Python"() {
        when:
        def result = executor.executeInline('result = {"a": 1, "b": 2}', [:])

        then:
        result instanceof Map
        result == [a: 1.0d, b: 2.0d]
    }

    def "can pass and receive ImageWrapper"() {
        given:
        def img = createImage(10, 10, 100.0f)

        when:
        def result = executor.executeInline("result = img", [img: img])

        then:
        result.is(img)
    }

    // ==================== Python Module Import Tests ====================

    def "can use jsolex module via global variable"() {
        when:
        def result = executor.executeInline('''
result = jsolex is not None
''', [:])

        then:
        result == true
    }

    def "can import jsolex module"() {
        when:
        def result = executor.executeInline('''
import jsolex
result = jsolex is not None
''', [:])

        then:
        result == true
    }

    def "can use from jsolex import syntax"() {
        when:
        def result = executor.executeInline('''
from jsolex import getVariable, setVariable
result = True
''', [:])

        then:
        result == true
    }

    // ==================== Bridge Function Tests ====================

    def "can access variables via bridge"() {
        given:
        evaluator.putVariable("myVar", 42.0)

        when:
        def result = executor.executeInline('''
result = jsolex.getVariable("myVar")
''', [:])

        then:
        result == 42.0d
    }

    def "can get image dimensions via bridge"() {
        given:
        def img = createImage(100, 50, 0.0f)

        when:
        def widthResult = executor.executeInline('result = img.width()', [img: img])
        def heightResult = executor.executeInline('result = img.height()', [img: img])

        then:
        widthResult == 100.0d
        heightResult == 50.0d
    }

    def "can create image via bridge"() {
        when:
        def result = executor.executeInline('''
data = [[1.0, 2.0], [3.0, 4.0]]
result = jsolex.createMono(2, 2, data)
''', [:])

        then:
        result instanceof ImageWrapper32
        def img = (ImageWrapper32) result
        img.width() == 2
        img.height() == 2
        img.data()[0][0] == 1.0f
        img.data()[0][1] == 2.0f
        img.data()[1][0] == 3.0f
        img.data()[1][1] == 4.0f
    }

    // ==================== Control Structure Tests ====================

    def "can use Python for loop"() {
        when:
        def result = executor.executeInline('''
total = 0
for i in range(5):
    total += i
result = total
''', [:])

        then:
        result == 10.0d  // 0+1+2+3+4
    }

    def "can use Python while loop"() {
        when:
        def result = executor.executeInline('''
i = 0
total = 0
while i < 5:
    total += i
    i += 1
result = total
''', [:])

        then:
        result == 10.0d
    }

    def "can use Python if/else"() {
        when:
        def resultTrue = executor.executeInline('''
if value > 5:
    result = "greater"
else:
    result = "not greater"
''', [value: 10.0])
        def resultFalse = executor.executeInline('''
if value > 5:
    result = "greater"
else:
    result = "not greater"
''', [value: 3.0])

        then:
        resultTrue == "greater"
        resultFalse == "not greater"
    }

    def "can use Python list comprehension"() {
        when:
        def result = executor.executeInline('''
result = [x * 2 for x in range(5)]
''', [:])

        then:
        result == [0.0d, 2.0d, 4.0d, 6.0d, 8.0d]
    }

    def "can use Python try/except"() {
        when:
        def result = executor.executeInline('''
try:
    x = 1 / 0
    result = "no error"
except:
    result = "error caught"
''', [:])

        then:
        result == "error caught"
    }

    // ==================== File-Based Script Tests ====================

    def "can execute Python file"() {
        given:
        def tempFile = Files.createTempFile("test_script", ".py")
        Files.writeString(tempFile, '''
x = 10
y = 20
result = x + y
''')

        when:
        def result = executor.executeFile(tempFile.toString(), [:])

        then:
        result == 30.0d

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    // ==================== Context Reuse Tests ====================

    def "context is reused across multiple calls"() {
        when:
        executor.executeInline("persistent_var = 42", [:])
        def result = executor.executeInline("result = persistent_var", [:])

        then:
        result == 42.0d
    }

    def "getOrCreate returns same instance"() {
        when:
        def executor1 = PythonScriptExecutor.getOrCreate(context, evaluator, Broadcaster.NO_OP)
        def executor2 = PythonScriptExecutor.getOrCreate(context, evaluator, Broadcaster.NO_OP)

        then:
        executor1.is(executor2)

    }

    // ==================== Error Handling Tests ====================

    def "Python syntax error throws exception"() {
        when:
        executor.executeInline("if if if", [:])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("Python error")
    }

    def "Python runtime error throws exception"() {
        when:
        executor.executeInline("undefined_variable", [:])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("Python error")
    }

    def "file not found throws exception"() {
        when:
        executor.executeInline("/nonexistent/path/script.py", [:])

        then:
        thrown(IllegalStateException)
    }

    // ==================== Edge Case Tests ====================

    def "empty script returns null"() {
        when:
        def result = executor.executeInline("", [:])

        then:
        result == null
    }

    def "script with only comments returns null"() {
        when:
        def result = executor.executeInline("# just a comment", [:])

        then:
        result == null
    }

    def "Unicode in script works"() {
        when:
        def result = executor.executeInline('result = "Bonjour \u00e0 tous"', [:])

        then:
        result == "Bonjour à tous"
    }

    def "can pass map from ImageMath to Python"() {
        when:
        def result = executor.executeInline('''
result = data["key"]
''', [data: [key: "value"]])

        then:
        result == "value"
    }

    def "can return nested structures"() {
        when:
        def result = executor.executeInline('''
result = {
    "numbers": [1, 2, 3],
    "nested": {"a": 1}
}
''', [:])

        then:
        result instanceof Map
        result["numbers"] == [1.0d, 2.0d, 3.0d]
        result["nested"] == [a: 1.0d]
    }

    // ==================== Coordinate Conversion Tests ====================

    def "imageToFrameCoords returns identity when no ReferenceCoords available"() {
        given: "an image without ReferenceCoords"
        def img = createImage(100, 100, 50.0f)

        when:
        def result = executor.executeInline('''
coords = jsolex.imageToFrameCoords(img, 50.0, 75.0, None)
result = [coords["frameNumber"], coords["xInFrame"], coords["available"]]
''', [img: img])

        then:
        result[0] == 75.0d  // frameNumber = y
        result[1] == 50.0d  // xInFrame = x
        result[2] == false  // not available
    }

    def "imageToFrameCoords uses ReferenceCoords when available"() {
        given: "an image with ReferenceCoords (hflip transform)"
        def refCoords = new ReferenceCoords([]).addHFlip(100.0)
        def metadata = [(ReferenceCoords): refCoords] as Map<Class<?>, Object>
        def img = new ImageWrapper32(100, 100, new float[100][100], metadata)

        when:
        def result = executor.executeInline('''
coords = jsolex.imageToFrameCoords(img, 30.0, 50.0, None)
result = [coords["frameNumber"], coords["xInFrame"], coords["available"]]
''', [img: img])

        then:
        result[0] == 50.0d  // frameNumber = y (unchanged by hflip)
        result[1] == 70.0d  // xInFrame = 100 - 30 (reversed hflip)
        result[2] == true   // available
    }

    def "imageToFrameCoords computes yInFrame when polynomial available"() {
        given: "an image with ReferenceCoords and SpectralLinePolynomial in context"
        def refCoords = new ReferenceCoords([])
        def metadata = [(ReferenceCoords): refCoords] as Map<Class<?>, Object>
        def img = new ImageWrapper32(100, 100, new float[100][100], metadata)

        // y = 0.0x³ + 0.0x² + 0.0x + 50.0 (constant polynomial)
        def polynomial = new SpectralLinePolynomial(new DoubleQuadruplet(0.0, 0.0, 0.0, 50.0))
        context.put(SpectralLinePolynomial, polynomial)

        when:
        def result = executor.executeInline('''
coords = jsolex.imageToFrameCoords(img, 30.0, 60.0, 5.0)
result = [coords["yInFrame"], coords["pixelShift"]]
''', [img: img])

        then:
        result[0] == 55.0d  // yInFrame = polynomial(30) + pixelShift = 50 + 5
        result[1] == 5.0d   // pixelShift
    }

    def "imageToFrameCoords uses image PixelShift metadata as default"() {
        given: "an image with PixelShift metadata"
        def refCoords = new ReferenceCoords([])
        def polynomial = new SpectralLinePolynomial(new DoubleQuadruplet(0.0, 0.0, 0.0, 50.0))
        def metadata = [
            (ReferenceCoords): refCoords,
            (PixelShift): new PixelShift(3.5)
        ] as Map<Class<?>, Object>
        def img = new ImageWrapper32(100, 100, new float[100][100], metadata)
        context.put(SpectralLinePolynomial, polynomial)

        when:
        def result = executor.executeInline('''
coords = jsolex.imageToFrameCoords(img, 30.0, 60.0, None)
result = [coords["yInFrame"], coords["pixelShift"]]
''', [img: img])

        then:
        result[0] == 53.5d  // yInFrame = 50 + 3.5
        result[1] == 3.5d   // pixelShift from metadata
    }

    def "frameToImageCoords reverses ReferenceCoords transformations"() {
        given: "an image with ReferenceCoords (hflip transform)"
        def refCoords = new ReferenceCoords([]).addHFlip(100.0)
        def metadata = [(ReferenceCoords): refCoords] as Map<Class<?>, Object>
        def img = new ImageWrapper32(100, 100, new float[100][100], metadata)

        when:
        def result = executor.executeInline('''
coords = jsolex.frameToImageCoords(img, 50, 70.0)
result = [coords["x"], coords["y"], coords["available"]]
''', [img: img])

        then:
        result[0] == 30.0d  // x = 100 - 70 (forward hflip)
        result[1] == 50.0d  // y = frameNumber
        result[2] == true   // available
    }

    def "frameToImageCoords handles complex transformations"() {
        given: "an image with multiple transforms"
        def refCoords = new ReferenceCoords([])
            .addScaleX(2.0)
            .addScaleY(0.5)
        def metadata = [(ReferenceCoords): refCoords] as Map<Class<?>, Object>
        def img = new ImageWrapper32(100, 100, new float[100][100], metadata)

        when:
        def result = executor.executeInline('''
coords = jsolex.frameToImageCoords(img, 80, 25.0)
result = [coords["x"], coords["y"]]
''', [img: img])

        then:
        result[0] == 50.0d  // x = 25 * 2.0
        result[1] == 40.0d  // y = 80 * 0.5
    }

    // ==================== Heliographic Coordinate Tests ====================

    def "heliographicToImage returns unavailable when solar params missing"() {
        given: "an image without solar parameters"
        def img = createImage(100, 100, 50.0f)

        when:
        def result = executor.executeInline('''
coords = jsolex.heliographicToImage(img, 0.0, 0.0)
result = coords["available"]
''', [img: img])

        then:
        result == false
    }

    def "heliographicToImage converts disk center correctly"() {
        given: "solar parameters and ellipse in context"
        // B0 = 0, P = 0 for simplicity
        // SolarParameters(carringtonRotation, b0, l0, p, apparentSize)
        def solarParams = new SolarParameters(2300, 0.0d, 0.0d, 0.0d, 0.0d)
        // Create a circle centered at (50, 50) with radius 40
        // Equation: (x-50)² + (y-50)² = 40² -> x² + y² - 100x - 100y + 5000 - 1600 = 0
        // a=1, b=0, c=1, d=-100, e=-100, f=3400
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -100.0d, -100.0d, 3400.0d))
        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)

        def img = createImage(100, 100, 50.0f)

        when: "converting heliographic (0, 0) - the disk center"
        def result = executor.executeInline('''
coords = jsolex.heliographicToImage(img, 0.0, 0.0)
result = [coords["x"], coords["y"], coords["visible"], coords["available"]]
''', [img: img])

        then:
        Math.abs((double) result[0] - 50.0) < 1.0  // x near center
        Math.abs((double) result[1] - 50.0) < 1.0  // y near center
        result[2] == true   // visible
        result[3] == true   // available
    }

    def "imageToHeliographic returns unavailable when ellipse missing"() {
        given: "solar parameters but no ellipse"
        // SolarParameters(carringtonRotation, b0, l0, p, apparentSize)
        def solarParams = new SolarParameters(2300, 0.0d, 0.0d, 0.0d, 0.0d)
        context.put(SolarParameters, solarParams)

        def img = createImage(100, 100, 50.0f)

        when:
        def result = executor.executeInline('''
coords = jsolex.imageToHeliographic(img, 50.0, 50.0)
result = coords["available"]
''', [img: img])

        then:
        result == false
    }

    def "imageToHeliographic converts disk center correctly"() {
        given: "solar parameters and ellipse in context"
        def solarParams = new SolarParameters(2300, 0.0d, 0.0d, 0.0d, 0.0d)
        // Circle at (50, 50) with radius 40: a=1, b=0, c=1, d=-100, e=-100, f=3400
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -100.0d, -100.0d, 3400.0d))
        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)

        def img = createImage(100, 100, 50.0f)

        when: "converting the disk center"
        def result = executor.executeInline('''
coords = jsolex.imageToHeliographic(img, 50.0, 50.0)
result = [coords["lat"], coords["lon"], coords["mu"], coords["onDisk"], coords["available"]]
''', [img: img])

        then:
        Math.abs((double) result[0]) < 1.0  // lat near 0
        Math.abs((double) result[1]) < 1.0  // lon near 0
        Math.abs((double) result[2] - 1.0) < 0.01  // mu near 1 at center
        result[3] == true   // on disk
        result[4] == true   // available
    }

    def "imageToHeliographic returns off disk for points outside solar disk"() {
        given: "solar parameters and ellipse in context"
        def solarParams = new SolarParameters(2300, 0.0d, 0.0d, 0.0d, 0.0d)
        // Circle at (50, 50) with radius 20: a=1, b=0, c=1, d=-100, e=-100, f=5000-400=4600
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -100.0d, -100.0d, 4600.0d))
        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)

        def img = createImage(100, 100, 50.0f)

        when: "converting a point far from center"
        def result = executor.executeInline('''
coords = jsolex.imageToHeliographic(img, 10.0, 10.0)
result = coords["onDisk"]
''', [img: img])

        then:
        result == false
    }

    def "heliographic to image round trip at disk center"() {
        given: "solar parameters and ellipse"
        def solarParams = new SolarParameters(2300, 0.0d, 0.0d, 0.0d, 0.0d)
        // Circle at (50, 50) with radius 40
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -100.0d, -100.0d, 3400.0d))
        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)

        def img = createImage(100, 100, 50.0f)

        when: "converting back and forth"
        def result = executor.executeInline('''
# Start with heliographic
helio = jsolex.heliographicToImage(img, 15.0, 25.0)
# Convert back to heliographic
back = jsolex.imageToHeliographic(img, helio["x"], helio["y"])
result = [helio["visible"], back["onDisk"], back["lat"], back["lon"]]
''', [img: img])

        then:
        result[0] == true  // visible
        result[1] == true  // on disk
        Math.abs((double) result[2] - 15.0) < 1.0  // lat preserved
        Math.abs((double) result[3] - 25.0) < 1.0  // lon preserved
    }

    def "getSolarParameters returns null when not set"() {
        when:
        def result = executor.executeInline('''
result = jsolex.getSolarParameters()
''', [:])

        then:
        result == null
    }

    def "getSolarParameters returns solar parameters when available"() {
        given: "solar parameters in context"
        // SolarParameters(carringtonRotation, b0, l0, p, apparentSize)
        def solarParams = new SolarParameters(
            2300,
            Math.toRadians(7.25d),   // B0 in radians
            Math.toRadians(45.0d),   // L0 in radians
            Math.toRadians(-10.5d),  // P in radians
            1920.0d
        )
        context.put(SolarParameters, solarParams)

        when:
        def result = executor.executeInline('''
params = jsolex.getSolarParameters()
result = [params["b0"], params["l0"], params["p"], params["carringtonRotation"]]
''', [:])

        then:
        Math.abs((double) result[0] - 7.25) < 0.01  // B0 in degrees
        Math.abs((double) result[1] - 45.0) < 0.01  // L0 in degrees
        Math.abs((double) result[2] - (-10.5)) < 0.01  // P in degrees
        result[3] == 2300.0d  // Carrington rotation
    }

    def "getEllipseParams returns ellipse info from image metadata"() {
        given: "an image with ellipse metadata"
        // Create circle at (100, 120) with semi-axes 80 and 75
        // For simplicity use a circle at (100, 120) with radius 77.5 (average)
        // Equation: (x-100)² + (y-120)² = 77.5² -> x² + y² - 200x - 240y + 10000 + 14400 - 6006.25 = 0
        // a=1, b=0, c=1, d=-200, e=-240, f=18393.75
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -200.0d, -240.0d, 18393.75d))
        def metadata = [(Ellipse): ellipse] as Map<Class<?>, Object>
        def img = new ImageWrapper32(200, 200, new float[200][200], metadata)

        when:
        def result = executor.executeInline('''
params = jsolex.getEllipseParams(img)
result = [params["centerX"], params["centerY"], params["semiAxisA"], params["semiAxisB"], params["radius"]]
''', [img: img])

        then:
        Math.abs((double) result[0] - 100.0) < 1.0  // centerX
        Math.abs((double) result[1] - 120.0) < 1.0  // centerY
        // For a circle, semiAxisA == semiAxisB == radius
        Math.abs((double) result[4] - 77.5) < 1.0   // average radius
    }

    // ==================== Coordinate Conversion Accuracy Tests ====================
    // These tests verify that heliographicToImage produces IDENTICAL results
    // to the built-in computeSphereCoords algorithm from SingleModeProcessingEventListener

    /**
     * Reference implementation of computeSphereCoords from SingleModeProcessingEventListener.
     * This is the EXACT algorithm used by the built-in rotation profile measurement.
     */
    private static double[] computeSphereCoords(double longitude, double colatitude, double radius, double b0, double angleP) {
        // Convert spherical to Cartesian (same formula as ImageDraw.ofSpherical)
        def x = Math.sin(longitude) * Math.sin(colatitude) * radius
        def y = Math.cos(colatitude) * radius
        def z = Math.cos(longitude) * Math.sin(colatitude) * radius

        // Rotate around X axis by -b0
        def cosB0 = Math.cos(-b0)
        def sinB0 = Math.sin(-b0)
        def y1 = y * cosB0 - z * sinB0
        def z1 = y * sinB0 + z * cosB0

        // Rotate around Z axis by -angleP
        def cosP = Math.cos(-angleP)
        def sinP = Math.sin(-angleP)
        def x2 = x * cosP - y1 * sinP
        def y2 = x * sinP + y1 * cosP

        return [x2, y2] as double[]
    }

    def "heliographicToImage matches built-in computeSphereCoords at equator"() {
        given: "solar parameters and ellipse"
        def b0Rad = Math.toRadians(b0Deg)
        def pRad = Math.toRadians(pDeg)
        def solarParams = new SolarParameters(2300, b0Rad, 0.0d, pRad, 0.0d)
        // Circle at (200, 200) with given radius: (x-200)² + (y-200)² = r²
        // Conic: x² + y² - 400x - 400y + (80000 - r²) = 0
        // a=1, b=0, c=1, d=-400, e=-400, f=80000-r²
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -400.0d, -400.0d, 80000.0d - radius*radius))
        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)
        def img = createImage(400, 400, 50.0f)

        and: "compute expected result using built-in algorithm"
        def latRad = Math.toRadians(latDeg)
        def lonRad = Math.toRadians(lonDeg)
        def colatitude = Math.PI / 2 - latRad
        def builtInCoords = computeSphereCoords(lonRad, colatitude, radius, b0Rad, pRad)
        def expectedX = 200.0 + builtInCoords[0]
        def expectedY = 200.0 + builtInCoords[1]

        when: "convert using bridge"
        def result = executor.executeInline("""
coords = jsolex.heliographicToImage(img, ${latDeg}, ${lonDeg})
result = [coords["x"], coords["y"], coords["visible"]]
""", [img: img])

        then: "results match exactly"
        Math.abs((double) result[0] - expectedX) < 0.001
        Math.abs((double) result[1] - expectedY) < 0.001

        where:
        latDeg | lonDeg | b0Deg | pDeg  | radius
        0.0    | 0.0    | 0.0   | 0.0   | 100.0   // disk center, no rotation
        0.0    | 75.0   | 0.0   | 0.0   | 100.0   // east limb
        0.0    | -75.0  | 0.0   | 0.0   | 100.0   // west limb
        0.0    | 0.0    | 7.25  | 0.0   | 100.0   // with B0
        0.0    | 0.0    | 0.0   | -10.5 | 100.0   // with P angle
        0.0    | 75.0   | 7.25  | -10.5 | 100.0   // east limb with both
    }

    def "heliographicToImage matches built-in computeSphereCoords at various latitudes"() {
        given: "solar parameters and ellipse"
        def b0Rad = Math.toRadians(b0Deg)
        def pRad = Math.toRadians(pDeg)
        def solarParams = new SolarParameters(2300, b0Rad, 0.0d, pRad, 0.0d)
        // Circle at (200, 200) with given radius
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -400.0d, -400.0d, 80000.0d - radius*radius))
        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)
        def img = createImage(400, 400, 50.0f)

        and: "compute expected result using built-in algorithm"
        def latRad = Math.toRadians(latDeg)
        def lonRad = Math.toRadians(lonDeg)
        def colatitude = Math.PI / 2 - latRad
        def builtInCoords = computeSphereCoords(lonRad, colatitude, radius, b0Rad, pRad)
        def expectedX = 200.0 + builtInCoords[0]
        def expectedY = 200.0 + builtInCoords[1]

        when: "convert using bridge"
        def result = executor.executeInline("""
coords = jsolex.heliographicToImage(img, ${latDeg}, ${lonDeg})
result = [coords["x"], coords["y"], coords["visible"]]
""", [img: img])

        then: "results match exactly"
        Math.abs((double) result[0] - expectedX) < 0.001
        Math.abs((double) result[1] - expectedY) < 0.001

        where:
        latDeg | lonDeg | b0Deg | pDeg  | radius
        30.0   | 75.0   | 0.0   | 0.0   | 100.0   // N30 east limb
        30.0   | -75.0  | 0.0   | 0.0   | 100.0   // N30 west limb
        -30.0  | 75.0   | 0.0   | 0.0   | 100.0   // S30 east limb
        -30.0  | -75.0  | 0.0   | 0.0   | 100.0   // S30 west limb
        45.0   | 60.0   | 0.0   | 0.0   | 100.0   // N45
        -45.0  | -60.0  | 0.0   | 0.0   | 100.0   // S45
        60.0   | 45.0   | 0.0   | 0.0   | 100.0   // N60
        -60.0  | -45.0  | 0.0   | 0.0   | 100.0   // S60
    }

    def "heliographicToImage matches built-in with realistic solar parameters"() {
        given: "realistic solar parameters (typical observation conditions)"
        def b0Rad = Math.toRadians(b0Deg)
        def pRad = Math.toRadians(pDeg)
        def solarParams = new SolarParameters(2300, b0Rad, 0.0d, pRad, 0.0d)
        // Circle at (200, 200) with given radius
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -400.0d, -400.0d, 80000.0d - radius*radius))
        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)
        def img = createImage(400, 400, 50.0f)

        and: "compute expected result using built-in algorithm"
        def latRad = Math.toRadians(latDeg)
        def lonRad = Math.toRadians(lonDeg)
        def colatitude = Math.PI / 2 - latRad
        def builtInCoords = computeSphereCoords(lonRad, colatitude, radius, b0Rad, pRad)
        def expectedX = 200.0 + builtInCoords[0]
        def expectedY = 200.0 + builtInCoords[1]

        when: "convert using bridge"
        def result = executor.executeInline("""
coords = jsolex.heliographicToImage(img, ${latDeg}, ${lonDeg})
result = [coords["x"], coords["y"], coords["visible"]]
""", [img: img])

        then: "results match exactly"
        Math.abs((double) result[0] - expectedX) < 0.001
        Math.abs((double) result[1] - expectedY) < 0.001

        where:
        latDeg | lonDeg | b0Deg | pDeg   | radius
        // Typical March observation: B0 ≈ -7°, P ≈ -26°
        30.0   | 75.0   | -7.0  | -26.0  | 100.0
        30.0   | -75.0  | -7.0  | -26.0  | 100.0
        -30.0  | 75.0   | -7.0  | -26.0  | 100.0
        -30.0  | -75.0  | -7.0  | -26.0  | 100.0
        0.0    | 75.0   | -7.0  | -26.0  | 100.0
        0.0    | -75.0  | -7.0  | -26.0  | 100.0
        // Typical June observation: B0 ≈ 0°, P ≈ 0°
        30.0   | 75.0   | 0.0   | 0.0    | 100.0
        // Typical September observation: B0 ≈ +7°, P ≈ +26°
        30.0   | 75.0   | 7.0   | 26.0   | 100.0
        30.0   | -75.0  | 7.0   | 26.0   | 100.0
    }

    def "east and west limbs at same latitude have different X but similar Y"() {
        given: "solar parameters with no rotation"
        def solarParams = new SolarParameters(2300, 0.0d, 0.0d, 0.0d, 0.0d)
        // Circle at (200, 200) with radius 100: d=-400, e=-400, f=80000-10000=70000
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -400.0d, -400.0d, 70000.0d))
        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)
        def img = createImage(400, 400, 50.0f)

        when: "convert east and west limbs at N30"
        def result = executor.executeInline('''
east = jsolex.heliographicToImage(img, 30.0, 75.0)
west = jsolex.heliographicToImage(img, 30.0, -75.0)
result = [east["x"], east["y"], west["x"], west["y"]]
''', [img: img])

        then: "X coordinates are symmetric around center, Y coordinates are equal"
        def eastX = (double) result[0]
        def eastY = (double) result[1]
        def westX = (double) result[2]
        def westY = (double) result[3]

        // East should be right of center (x > 200), west should be left (x < 200)
        eastX > 200.0
        westX < 200.0
        // X should be symmetric: (eastX - 200) ≈ -(westX - 200)
        Math.abs((eastX - 200.0) + (westX - 200.0)) < 0.001
        // Y should be identical for same latitude
        Math.abs(eastY - westY) < 0.001
    }

    def "east and west limbs map to different frames but same column with LEFT_ROTATION"() {
        given: "solar parameters, ellipse, and ReferenceCoords with LEFT_ROTATION"
        def solarParams = new SolarParameters(2300, 0.0d, 0.0d, 0.0d, 0.0d)
        // Circle at (200, 200) with radius 100
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -400.0d, -400.0d, 70000.0d))
        // LEFT_ROTATION with originalHeight = 3000 (typical SER frame height)
        def refCoords = new ReferenceCoords([]).addLeftRotation(3000.0)
        def polynomial = new SpectralLinePolynomial(new DoubleQuadruplet(0.0, 0.0, 0.0, 60.0))

        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)
        context.put(SpectralLinePolynomial, polynomial)

        def metadata = [(ReferenceCoords): refCoords] as Map<Class<?>, Object>
        def img = new ImageWrapper32(400, 400, new float[400][400], metadata)

        when: "convert east and west limbs at N30 to image coords, then to frame coords"
        def result = executor.executeInline('''
# Get image coordinates for east and west limbs at N30
east_img = jsolex.heliographicToImage(img, 30.0, 75.0)
west_img = jsolex.heliographicToImage(img, 30.0, -75.0)

# Get frame coordinates
east_frame = jsolex.imageToFrameCoords(img, east_img["x"], east_img["y"], 0.0)
west_frame = jsolex.imageToFrameCoords(img, west_img["x"], west_img["y"], 0.0)

result = {
    "east_imgX": east_img["x"],
    "east_imgY": east_img["y"],
    "west_imgX": west_img["x"],
    "west_imgY": west_img["y"],
    "east_frame": east_frame["frameNumber"],
    "east_column": east_frame["xInFrame"],
    "west_frame": west_frame["frameNumber"],
    "west_column": west_frame["xInFrame"],
}
''', [img: img])

        then: "east and west have different image X but same image Y"
        def eastImgX = result["east_imgX"]
        def westImgX = result["west_imgX"]
        def eastImgY = result["east_imgY"]
        def westImgY = result["west_imgY"]

        // Image X should be different (east is right of center, west is left)
        Math.abs(eastImgX - westImgX) > 50.0
        // Image Y should be the same (same latitude)
        Math.abs(eastImgY - westImgY) < 0.001

        and: "after LEFT_ROTATION transform, they map to DIFFERENT frames but SAME column"
        def eastFrame = result["east_frame"]
        def westFrame = result["west_frame"]
        def eastColumn = result["east_column"]
        def westColumn = result["west_column"]

        // Frames should be different (critical for Doppler measurement!)
        Math.abs(eastFrame - westFrame) > 50
        // Columns should be the same (same latitude = same slit position)
        Math.abs(eastColumn - westColumn) < 1.0
    }

    def "east and west limbs map to different frames with RIGHT_ROTATION"() {
        given: "solar parameters, ellipse, and ReferenceCoords with RIGHT_ROTATION"
        def solarParams = new SolarParameters(2300, 0.0d, 0.0d, 0.0d, 0.0d)
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -400.0d, -400.0d, 70000.0d))
        // RIGHT_ROTATION with originalWidth = 3000
        def refCoords = new ReferenceCoords([]).addRightRotation(3000.0)
        def polynomial = new SpectralLinePolynomial(new DoubleQuadruplet(0.0, 0.0, 0.0, 60.0))

        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)
        context.put(SpectralLinePolynomial, polynomial)

        def metadata = [(ReferenceCoords): refCoords] as Map<Class<?>, Object>
        def img = new ImageWrapper32(400, 400, new float[400][400], metadata)

        when: "convert east and west limbs to frame coords"
        def result = executor.executeInline('''
east_img = jsolex.heliographicToImage(img, 30.0, 75.0)
west_img = jsolex.heliographicToImage(img, 30.0, -75.0)
east_frame = jsolex.imageToFrameCoords(img, east_img["x"], east_img["y"], 0.0)
west_frame = jsolex.imageToFrameCoords(img, west_img["x"], west_img["y"], 0.0)
result = [east_frame["frameNumber"], west_frame["frameNumber"]]
''', [img: img])

        then: "frames should be different"
        Math.abs((int) result[0] - (int) result[1]) > 50
    }

    def "full pipeline: heliographic to frame coordinates at multiple latitudes"() {
        given: "complete metadata setup simulating real processing"
        def b0Rad = Math.toRadians(-7.0)  // Typical March B0
        def pRad = Math.toRadians(-26.0)   // Typical March P
        def solarParams = new SolarParameters(2300, b0Rad, 0.0d, pRad, 0.0d)
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -400.0d, -400.0d, 70000.0d))
        def refCoords = new ReferenceCoords([]).addLeftRotation(3000.0)
        def polynomial = new SpectralLinePolynomial(new DoubleQuadruplet(0.0, 0.0, 0.0, 60.0))

        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)
        context.put(SpectralLinePolynomial, polynomial)

        def metadata = [(ReferenceCoords): refCoords] as Map<Class<?>, Object>
        def img = new ImageWrapper32(400, 400, new float[400][400], metadata)

        when: "measure frame differences at various latitudes"
        def result = executor.executeInline('''
results = []
for lat in [-60, -30, 0, 30, 60]:
    east = jsolex.heliographicToImage(img, float(lat), 75.0)
    west = jsolex.heliographicToImage(img, float(lat), -75.0)
    if east["visible"] and west["visible"]:
        east_frame = jsolex.imageToFrameCoords(img, east["x"], east["y"], 0.0)
        west_frame = jsolex.imageToFrameCoords(img, west["x"], west["y"], 0.0)
        frame_diff = abs(east_frame["frameNumber"] - west_frame["frameNumber"])
        results.append([lat, frame_diff])
result = results
''', [img: img])

        then: "all latitudes should show significant frame differences (critical for Doppler)"
        result.each { entry ->
            def lat = entry[0]
            def frameDiff = entry[1]

            // Frame difference should be significant (indicates different scan positions)
            // This is CRITICAL - east and west MUST read from different frames for Doppler measurement
            assert frameDiff > 30 : "Frame diff at lat $lat should be > 30, got $frameDiff"
        }
    }

    def "with P=0 and B0=0, east and west at same latitude have same column"() {
        given: "solar parameters with no P or B0 rotation"
        def solarParams = new SolarParameters(2300, 0.0d, 0.0d, 0.0d, 0.0d)
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -400.0d, -400.0d, 70000.0d))
        def refCoords = new ReferenceCoords([]).addLeftRotation(3000.0)
        def polynomial = new SpectralLinePolynomial(new DoubleQuadruplet(0.0, 0.0, 0.0, 60.0))

        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)
        context.put(SpectralLinePolynomial, polynomial)

        def metadata = [(ReferenceCoords): refCoords] as Map<Class<?>, Object>
        def img = new ImageWrapper32(400, 400, new float[400][400], metadata)

        when: "convert east and west at same latitude"
        def result = executor.executeInline('''
east = jsolex.heliographicToImage(img, 30.0, 75.0)
west = jsolex.heliographicToImage(img, 30.0, -75.0)
east_frame = jsolex.imageToFrameCoords(img, east["x"], east["y"], 0.0)
west_frame = jsolex.imageToFrameCoords(img, west["x"], west["y"], 0.0)
result = {
    "east_imgY": east["y"],
    "west_imgY": west["y"],
    "east_col": east_frame["xInFrame"],
    "west_col": west_frame["xInFrame"],
    "frame_diff": abs(east_frame["frameNumber"] - west_frame["frameNumber"])
}
''', [img: img])

        then: "with no P rotation, imgY should be same -> same column"
        // Without P rotation, east and west at same latitude have same imgY
        Math.abs(result["east_imgY"] - result["west_imgY"]) < 0.001
        // Same imgY -> same column after LEFT_ROTATION
        Math.abs(result["east_col"] - result["west_col"]) < 1.0
        // But different frames (critical!)
        result["frame_diff"] > 50
    }

    def "north and south latitudes have different Y coordinates"() {
        given: "solar parameters with no rotation"
        def solarParams = new SolarParameters(2300, 0.0d, 0.0d, 0.0d, 0.0d)
        // Circle at (200, 200) with radius 100: d=-400, e=-400, f=70000
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1.0d, 0.0d, 1.0d, -400.0d, -400.0d, 70000.0d))
        context.put(SolarParameters, solarParams)
        context.put(Ellipse, ellipse)
        def img = createImage(400, 400, 50.0f)

        when: "convert north and south points at same longitude"
        def result = executor.executeInline('''
north = jsolex.heliographicToImage(img, 30.0, 0.0)
south = jsolex.heliographicToImage(img, -30.0, 0.0)
center = jsolex.heliographicToImage(img, 0.0, 0.0)
result = [north["x"], north["y"], south["x"], south["y"], center["x"], center["y"]]
''', [img: img])

        then: "north is above center, south is below (in image coordinates)"
        def northY = (double) result[1]
        def southY = (double) result[3]
        def centerY = (double) result[5]

        // Y coordinates should show north above south
        // (depending on image orientation, but they should be different and symmetric)
        Math.abs(northY - southY) > 10.0  // Definitely different
        // Should be symmetric around center
        Math.abs((northY - centerY) + (southY - centerY)) < 0.001
    }

    // ==================== Relative Path Resolution Tests ====================

    def "python_file resolves relative paths from ImageMath script directory"() {
        given: "a temp directory with an ImageMath script and a Python file"
        def tempDir = Files.createTempDirectory("imagemath_test")
        def subDir = Files.createDirectory(tempDir.resolve("scripts"))
        def pythonFile = subDir.resolve("helper.py")
        Files.writeString(pythonFile, '''
x = 100
y = 23
result = x + y
''')
        def mathScript = tempDir.resolve("test.math")
        Files.writeString(mathScript, '''
[outputs]
answer = python_file("scripts/helper.py")
''')

        and: "a DefaultImageScriptExecutor"
        def scriptExecutor = new DefaultImageScriptExecutor(
            { shift -> new ImageWrapper32(10, 10, new float[10][10], [:]) },
            [:]
        )

        when: "the ImageMath script is executed from its path"
        def result = scriptExecutor.execute(mathScript, SectionKind.SINGLE)

        then: "the python_file resolves the relative path correctly"
        result.valuesByLabel().size() == 1
        result.valuesByLabel()['answer'] == 123.0d

        cleanup:
        Files.deleteIfExists(pythonFile)
        Files.deleteIfExists(mathScript)
        Files.deleteIfExists(subDir)
        Files.deleteIfExists(tempDir)
    }

    def "python_file works within custom user functions"() {
        given: "a temp directory with an ImageMath script and Python files"
        def tempDir = Files.createTempDirectory("imagemath_userfunc_test")
        def pythonFile = tempDir.resolve("compute.py")
        Files.writeString(pythonFile, '''
import jsolex
a = jsolex.getVariable('a')
b = jsolex.getVariable('b')
result = a * b + 10
''')
        def mathScript = tempDir.resolve("test.math")
        Files.writeString(mathScript, '''
[fun:multiply a b]
result = python_file("compute.py")

[outputs]
answer = multiply(5; 7)
''')

        and: "a DefaultImageScriptExecutor"
        def scriptExecutor = new DefaultImageScriptExecutor(
            { shift -> new ImageWrapper32(10, 10, new float[10][10], [:]) },
            [:]
        )

        when: "the ImageMath script is executed"
        def result = scriptExecutor.execute(mathScript, SectionKind.SINGLE)

        then: "the custom function using python_file works correctly"
        result.valuesByLabel().size() == 1
        result.valuesByLabel()['answer'] == 45.0d  // 5 * 7 + 10

        cleanup:
        Files.deleteIfExists(pythonFile)
        Files.deleteIfExists(mathScript)
        Files.deleteIfExists(tempDir)
    }

    def "python_file can import from other Python files in same directory"() {
        given: "a temp directory with an ImageMath script and Python files with imports"
        def tempDir = Files.createTempDirectory("imagemath_import_test")

        // Create a utility module
        def utilsFile = tempDir.resolve("math_utils.py")
        Files.writeString(utilsFile, '''
def add(a, b):
    return a + b

def multiply(a, b):
    return a * b
''')

        // Create the main script that imports from utils
        def mainFile = tempDir.resolve("main.py")
        Files.writeString(mainFile, '''
import jsolex
from math_utils import add, multiply

x = jsolex.getVariable('x')
y = jsolex.getVariable('y')
result = add(x, y) + multiply(x, y)
''')

        def mathScript = tempDir.resolve("test.math")
        Files.writeString(mathScript, '''
[fun:compute x y]
result = python_file("main.py")

[outputs]
answer = compute(3; 4)
''')

        and: "a DefaultImageScriptExecutor"
        def scriptExecutor = new DefaultImageScriptExecutor(
            { shift -> new ImageWrapper32(10, 10, new float[10][10], [:]) },
            [:]
        )

        when: "the ImageMath script is executed"
        def result = scriptExecutor.execute(mathScript, SectionKind.SINGLE)

        then: "the Python import works and computation is correct"
        result.valuesByLabel().size() == 1
        result.valuesByLabel()['answer'] == 19.0d  // add(3,4) + multiply(3,4) = 7 + 12

        cleanup:
        Files.deleteIfExists(utilsFile)
        Files.deleteIfExists(mainFile)
        Files.deleteIfExists(mathScript)
        Files.deleteIfExists(tempDir)
    }

    def "imported Python modules are reloaded when modified"() {
        given: "a temp directory with Python files"
        def tempDir = Files.createTempDirectory("python_reload_test")

        // Create a utility module with initial implementation
        def utilsFile = tempDir.resolve("myutils.py")
        Files.writeString(utilsFile, '''
VALUE = 10

def get_value():
    return VALUE
''')

        // Create the main script that imports the module and calls a function
        // This tests that changes to the module are picked up
        def mainFile = tempDir.resolve("main.py")
        Files.writeString(mainFile, '''
import myutils
result = myutils.get_value()
''')

        when: "the script is executed first time"
        def result1 = executor.executeFile(mainFile.toString(), [:])

        then: "the result uses original value"
        result1 == 10.0d

        when: "the utility module is modified"
        Files.writeString(utilsFile, '''
VALUE = 999

def get_value():
    return VALUE
''')

        and: "the script is executed again"
        def result2 = executor.executeFile(mainFile.toString(), [:])

        then: "the result reflects the updated module"
        result2 == 999.0d

        cleanup:
        Files.deleteIfExists(utilsFile)
        Files.deleteIfExists(mainFile)
        Files.deleteIfExists(tempDir)
    }

    def "imported Python modules with from-import are reloaded when modified"() {
        given: "a temp directory with Python files using from-import syntax"
        def tempDir = Files.createTempDirectory("python_reload_from_test")

        // Create a utility module with initial implementation
        def utilsFile = tempDir.resolve("myutils2.py")
        Files.writeString(utilsFile, '''
def compute(x):
    return x * 10
''')

        // Create the main script using from-import
        def mainFile = tempDir.resolve("main2.py")
        Files.writeString(mainFile, '''
from myutils2 import compute
result = compute(5)
''')

        when: "the script is executed first time"
        def result1 = executor.executeFile(mainFile.toString(), [:])

        then: "the result uses original multiplier"
        result1 == 50.0d

        when: "the utility module is modified"
        Files.writeString(utilsFile, '''
def compute(x):
    return x * 100
''')

        and: "the script is executed again"
        def result2 = executor.executeFile(mainFile.toString(), [:])

        then: "the result reflects the updated module"
        result2 == 500.0d

        cleanup:
        Files.deleteIfExists(utilsFile)
        Files.deleteIfExists(mainFile)
        Files.deleteIfExists(tempDir)
    }

    def "errors in modified Python modules are detected on re-import"() {
        given: "a temp directory with Python files using from-import syntax"
        def tempDir = Files.createTempDirectory("python_error_detection_test")

        // Create a utility module with working implementation
        def utilsFile = tempDir.resolve("myutils_err.py")
        Files.writeString(utilsFile, '''
def compute(x):
    return x * 10
''')

        // Create the main script using from-import
        def mainFile = tempDir.resolve("main_err.py")
        Files.writeString(mainFile, '''
from myutils_err import compute
result = compute(5)
''')

        when: "the script is executed first time"
        def result1 = executor.executeFile(mainFile.toString(), [:])

        then: "the result is correct"
        result1 == 50.0d

        when: "the utility module is modified to have a syntax error"
        Files.writeString(utilsFile, '''
def compute(x):
    return x *   # syntax error: incomplete expression
''')

        and: "the script is executed again"
        executor.executeFile(mainFile.toString(), [:])

        then: "the error in the modified module is detected"
        thrown(IllegalStateException)

        cleanup:
        Files.deleteIfExists(utilsFile)
        Files.deleteIfExists(mainFile)
        Files.deleteIfExists(tempDir)
    }

    def "imported Python modules via ImageMath python_file are reloaded when modified"() {
        given: "a temp directory with Python files using from-import syntax"
        def tempDir = Files.createTempDirectory("imagemath_reload_test")

        // Create a utility module with initial implementation
        def utilsFile = tempDir.resolve("myutils3.py")
        Files.writeString(utilsFile, '''
def compute(x):
    return x * 10
''')

        // Create the main script using from-import
        def mainFile = tempDir.resolve("main3.py")
        Files.writeString(mainFile, '''
from myutils3 import compute
result = compute(5)
''')

        and: "an ImageMath script that calls python_file via a user function"
        def imageMathScript = """
[fun:run_script]
result = python_file("${mainFile.toString().replace('\\', '/')}")

[outputs]
value = run_script()
"""

        and: "a DefaultImageScriptExecutor"
        def scriptExecutor = new DefaultImageScriptExecutor(
            { shift -> null } as java.util.function.Function,
            context,
            Broadcaster.NO_OP
        )
        scriptExecutor.setIncludesDir(tempDir)

        when: "the script is executed first time"
        def result1 = scriptExecutor.execute(imageMathScript, SectionKind.SINGLE)

        then: "the result uses original multiplier"
        result1.valuesByLabel()["value"] == 50.0d

        when: "the utility module is modified"
        Files.writeString(utilsFile, '''
def compute(x):
    return x * 100
''')

        and: "the script is executed again"
        def result2 = scriptExecutor.execute(imageMathScript, SectionKind.SINGLE)

        then: "the result reflects the updated module"
        result2.valuesByLabel()["value"] == 500.0d

        cleanup:
        Files.deleteIfExists(utilsFile)
        Files.deleteIfExists(mainFile)
        Files.deleteIfExists(tempDir)
    }

    // ==================== MemoizingExpressionEvaluator Integration Tests ====================

    def "user function calling python_file is not cached by MemoizingExpressionEvaluator"() {
        given: "a Python script that reads current time (different each call)"
        def tempDir = Files.createTempDirectory("user_func_cache_test")
        def pythonFile = tempDir.resolve("script.py")
        Files.writeString(pythonFile, '''
import time
# Using time.time() * 1000 gives milliseconds that fit in a double
result = time.time() * 1000
''')

        and: "an ImageMath script that calls a user function twice in the same execution"
        def imageMathScript = """
[fun:get_time]
result = python_file("${pythonFile.toString().replace('\\', '/')}")

[outputs]
first_call = get_time()
second_call = get_time()
"""

        and: "a DefaultImageScriptExecutor"
        def scriptExecutor = new DefaultImageScriptExecutor(
            { shift -> null } as java.util.function.Function,
            context,
            Broadcaster.NO_OP
        )
        scriptExecutor.setIncludesDir(tempDir)

        when: "the script is executed"
        def result = scriptExecutor.execute(imageMathScript, SectionKind.SINGLE)

        then: "both calls returned different values (not cached)"
        // If caching occurred, both would have the same timestamp
        result.valuesByLabel()["first_call"] != null
        result.valuesByLabel()["second_call"] != null
        result.valuesByLabel()["first_call"] != result.valuesByLabel()["second_call"]

        cleanup:
        Files.deleteIfExists(pythonFile)
        Files.deleteIfExists(tempDir)
    }

    // ==================== Helper Methods ====================

    private static ImageWrapper32 createImage(int width, int height, float value) {
        float[][] data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = value
            }
        }
        new ImageWrapper32(width, height, data, [:])
    }
}
