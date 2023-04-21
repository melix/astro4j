/*
 * Copyright 2003-2021 the original author or authors.
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
package me.champeau.a4j.math.fft

import spock.lang.Specification

class FFTSupportTest extends Specification {
    def "#n is power of 2 (#expected)"() {
        expect:
        FFTSupport.isPowerOf2(n) == expected

        where:
        n | expected
        0 | true
        1 | true
        2 | true
        3 | false
        4 | true
        5 | false
        6 | false
        7 | false
        8 | true
    }

    def "next power of 2 of #n is #expected"() {
        expect:
        FFTSupport.nextPowerOf2(n) == expected

        where:
        n    | expected
        0    | 0
        1    | 1
        2    | 2
        3    | 4
        4    | 4
        5    | 8
        6    | 8
        7    | 8
        8    | 8
        9    | 16
        1234 | 2048
    }
}
