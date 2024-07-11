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
module me.champeau.a4j.math {
    exports me.champeau.a4j.math;
    exports me.champeau.a4j.math.fft;
    exports me.champeau.a4j.math.image;
    exports me.champeau.a4j.math.matrix;
    exports me.champeau.a4j.math.regression;
    exports me.champeau.a4j.math.tuples;
    exports me.champeau.a4j.math.image.analysis;
    requires jdk.incubator.vector;
    requires commons.math3;
}
