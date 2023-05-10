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
package me.champeau.a4j.jsolex.processing.params;

import java.util.OptionalDouble;

public class GeometryParams {
    private final Double tilt;
    private final Double xyRatio;
    private final boolean horizontalMirror;
    private final boolean verticalMirror;

    public GeometryParams(Double tilt, Double xyRatio, boolean horizontalMirror, boolean verticalMirror) {
        this.tilt = tilt;
        this.xyRatio = xyRatio;
        this.horizontalMirror = horizontalMirror;
        this.verticalMirror = verticalMirror;
    }

    public OptionalDouble tilt() {
        return tilt == null ? OptionalDouble.empty() : OptionalDouble.of(tilt);
    }

    public OptionalDouble xyRatio() {
        return xyRatio == null ? OptionalDouble.empty() : OptionalDouble.of(xyRatio);
    }

    public boolean isHorizontalMirror() {
        return horizontalMirror;
    }

    public boolean isVerticalMirror() {
        return verticalMirror;
    }

    public GeometryParams withTilt(Double tilt) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror);
    }

    public GeometryParams withXYRatio(Double xyRatio) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror);
    }

    public GeometryParams withHorizontalMirror(boolean horizontalMirror) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror);
    }

    public GeometryParams withVerticalMirror(boolean verticalMirror) {
        return new GeometryParams(tilt, xyRatio, horizontalMirror, verticalMirror);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("GeometryParams[");
        if (tilt != null) {
            sb.append("tilt=").append(tilt);
        }
        if (xyRatio != null) {
            sb.append(", xyRatio=").append(xyRatio);
        }
        sb.append(", horizontalMirror=").append(horizontalMirror)
                .append(", verticalMirror=").append(verticalMirror)
                .append("]");
        return sb.toString();
    }
}
