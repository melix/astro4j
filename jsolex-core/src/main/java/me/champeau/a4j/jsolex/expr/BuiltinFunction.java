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
package me.champeau.a4j.jsolex.expr;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public enum BuiltinFunction {

    A2PX(req("a", "angstroms"), opt("ref", "reference wavelength")),
    ADJUST_CONTRAST(Parameter.IMG, req("min", "min value"), req("max", "max value")),
    ADJUST_GAMMA(Parameter.IMG, req("gamma", "gamma value")),
    ANIM(req("images", "list of images"), opt("delay", "delay (ms)")),
    AR_OVERLAY(Parameter.IMG, opt("labels", "show labels")),
    ASINH_STRETCH(Parameter.IMG, req("bp", "black point"), req("stretch", "stretch value")),
    AUTOCROP(Parameter.IMG, opt("ellipse", "ellipse")),
    AUTOCROP2(Parameter.IMG, opt("factor", "factor"), opt("rounding", "rounding"), opt("ellipse", "ellipse")),
    AUTO_CONTRAST(Parameter.IMG, req("gamma", "gamma value")),
    AVG(Parameter.SPREAD_LIST),
    BG_MODEL(Parameter.IMG, opt("order", "order"), opt("sigma", "sigma")),
    BLUR(Parameter.IMG, opt("kernel", "kernel size")),
    CHOOSE_FILE(req("id", "identifier"), req("message", "title")),
    CHOOSE_FILES(req("id", "identifier"), req("message", "title")),
    CLAHE(Parameter.IMG, req("ts", "tile size"), req("bins", "bins"), req("clip", "clip limit")),
    COLORIZE(Parameter.IMG, req("rIn"), req("rOut"), req("gIn"), req("gOut"), req("bIn"), req("bOut"), req("color")),
    CONCAT(Parameter.SPREAD_LIST),
    CONTINUUM(),
    CROP(Parameter.IMG, req("left"), req("top"), req("width"), req("height")),
    CROP_AR(Parameter.IMG, opt("ms", "minimum size"), opt("margin", "margin (%)")),
    CROP_RECT(Parameter.IMG, req("width"), req("height"), opt("ellipse")),
    DEDISTORT(req("ref"), Parameter.IMG, opt("ts", "tile size"), opt("sampling"), opt("threshold")),
    DISK_FILL(Parameter.IMG, opt("color"), opt("ellipse")),
    DISK_MASK(Parameter.IMG, opt("invert", "0 or 1")),
    DRAW_ARROW(Parameter.IMG, req("x1"), req("y1"), req("x2"), req("y2"), opt("thickness"), opt("color")),
    DRAW_CIRCLE(Parameter.IMG, req("cx", "center x"), req("cy", "center y"), req("radius"), opt("thickness"), opt("color")),
    DRAW_EARTH(Parameter.IMG, opt("x", "x coordinate"), opt("y", "y coordinate")),
    DRAW_GLOBE(Parameter.IMG, opt("angleP"), opt("b0"), opt("ellipse")),
    DRAW_OBS_DETAILS(Parameter.IMG, opt("x", "x coordinate"), opt("y", "y coordinate")),
    DRAW_RECT(Parameter.IMG, req("x", "x coordinate"), req("y", "y coordinate"), req("width"), req("height"), opt("thickness"), opt("color")),
    DRAW_SOLAR_PARAMS(Parameter.IMG, opt("x", "x coordinate"), opt("y", "y coordinate"), opt("ellipse")),
    DRAW_TEXT(Parameter.IMG, req("x", "x coordinate"), req("y", "y coordinate"), req("text"), opt("fs", "font size"), opt("color")),
    ELLIPSE_FIT(Parameter.IMG),
    EQUALIZE(req("list")),
    EXP(Parameter.ANY, req("exp", "exponent")),
    FILTER(Parameter.IMG, req("subject"), req("function"), req("value")),
    FIND_SHIFT(req("wl", "wavelength in angstroms or spectral ray name"), opt("ref", "wavelength or spectral ray of reference")),
    FIX_BANDING(Parameter.IMG, req("bs", "band size"), req("passes"), opt("ellipse")),
    FIX_GEOMETRY(Parameter.IMG),
    FLAT_CORRECTION(Parameter.IMG, opt("lo", "low percentile"), opt("hi", "high percentile"), opt("order")),
    GET_AT(req("list"), req("index", "index")),
    GET_B(Parameter.IMG),
    GET_G(Parameter.IMG),
    GET_R(Parameter.IMG),
    HFLIP(Parameter.IMG),
    IMG(req("ps", "pixel shift")),
    INVERT(Parameter.IMG),
    LINEAR_STRETCH(Parameter.IMG, opt("lo", "low value"), opt("hi", "high value")),
    LIST(Parameter.SPREAD_LIST),
    LOAD(req("file", "file path")),
    LOAD_MANY(req("dir", "directory"), opt("pattern")),
    LOG(Parameter.ANY, req("exp", "exponent")),
    MAX(Parameter.SPREAD_LIST),
    MEDIAN(Parameter.SPREAD_LIST),
    MIN(Parameter.SPREAD_LIST),
    MONO(Parameter.IMG),
    MOSAIC(req("images"), opt("ts", "tile size"), opt("sampling")),
    NEUTRALIZE_BG(Parameter.IMG, opt("iterations")),
    POW(Parameter.ANY, req("exp", "exponent")),
    PX2A(req("px", "pixels"), opt("ref", "reference wavelength in angstroms")),
    RADIUS_RESCALE(req("images")),
    RANGE(req("from"), req("to"), opt("step")),
    REMOTE_SCRIPTGEN(req("url")),
    REMOVE_BG(Parameter.IMG, opt("tolerance"), opt("ellipse")),
    RESCALE_ABS(Parameter.IMG, req("width"), req("height")),
    RESCALE_REL(Parameter.IMG, req("sx", "scale x"), req("sy", "scale y")),
    RGB(req("r", "red"), req("g", "green"), req("b", "blue")),
    RL_DECON(Parameter.IMG, opt("radius"), opt("sigma"), opt("iterations")),
    ROTATE_DEG(Parameter.IMG, req("angle", "angle in degrees"), opt("bp", "black point"), opt("resize")),
    ROTATE_LEFT(Parameter.IMG),
    ROTATE_RAD(Parameter.IMG, req("angle", "angle in radians"), opt("bp", "black point"), opt("resize")),
    ROTATE_RIGHT(Parameter.IMG),
    SATURATE(Parameter.IMG, req("factor", "saturation factor")),
    SHARPEN(Parameter.IMG, opt("kernel", "kernel size")),
    SORT(req("images"), req("order", "sort order")),
    STACK(req("images"), opt("ts", "tile size"), opt("sampling"), opt("select", "reference selection")),
    STACK_REF(req("images"), opt("select", "reference selection")),
    TRANSITION(req("images"), req("steps", "nb of steps"), opt("type", "transition type"), opt("unit" , "ips/ipm/iph")),
    VFLIP(Parameter.IMG),
    VIDEO_DATETIME(Parameter.IMG, opt("format")),
    WAVELEN(Parameter.IMG),
    WEIGHTED_AVG(req("images"), opt("weights")),
    WORKDIR(true, List.of(req("dir", "directory")));

    private final boolean hasSideEffect;
    private final List<Parameter> parameters;


    private static Parameter req(String name) {
        return req(name, name);
    }

    private static Parameter req(String name, String description) {
        return new Parameter(name, description, true);
    }

    private static Parameter opt(String name) {
        return opt(name, name);
    }

    private static Parameter opt(String name, String description) {
        return new Parameter(name, description, false);
    }

    BuiltinFunction() {
        this(false, List.of());
    }

    BuiltinFunction(boolean hasSideEffect, List<Parameter> parameters) {
        this.hasSideEffect = hasSideEffect;
        this.parameters = parameters;
    }

    BuiltinFunction(Parameter... parameters) {
        this(false, Arrays.stream(parameters).toList());
    }

    BuiltinFunction(String... parameters) {
        this(false, Arrays.stream(parameters).map(s -> new Parameter(s, null, true)).toList());
    }

    public String lowerCaseName() {
        return name().toLowerCase(Locale.US);
    }

    static BuiltinFunction of(String value) {
        return valueOf(value.toUpperCase(Locale.US));
    }

    public boolean hasSideEffect() {
        return hasSideEffect;
    }

    public void validateArgs(Map<String, Object> args) {
        if (this.parameters.size() == 1 && Parameter.SPREAD_LIST.equals(this.parameters.getFirst())) {
            return;
        }
        var keys = args.keySet();
        var required = parameters.stream().filter(Parameter::required).map(Parameter::name).collect(Collectors.toSet());
        var missing = required.stream().filter(k -> !keys.contains(k)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            var sb = new StringBuilder();
            sb.append("Function '")
                    .append(name())
                    .append("' is missing required arguments: ");
            for (String key : missing) {
                sb.append(key).append(", ");
            }
            sb.setLength(sb.length() - 2); // remove last comma
            throw new IllegalArgumentException(sb.toString());
        }
        var unknown = keys.stream().filter(k -> parameters.stream().noneMatch(p -> p.name.equals(k))).collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            var sb = new StringBuilder();
            sb.append("Function '")
                    .append(name())
                    .append("' has unknown arguments: ");
            for (String key : unknown) {
                sb.append(key).append(", ");
            }
            sb.setLength(sb.length() - 2); // remove last comma
            throw new IllegalArgumentException(sb.toString());
        }
    }

    /**
     * Maps the positional arguments to a map of named arguments.
     * @param args the positional arguments
     * @return a map of named arguments
     */
    public Map<String, Object> mapPositionalArguments(List<Object> args) {
        if (this.parameters.size() == 1 && Parameter.SPREAD_LIST.equals(this.parameters.getFirst())) {
            return Map.of("list", args);
        }
        // special case for colorize
        if (this == COLORIZE) {
            if (args.size() == 7) {
                return Map.of(
                        "img", args.getFirst(),
                        "rIn", args.get(1),
                        "rOut", args.get(2),
                        "gIn", args.get(3),
                        "gOut", args.get(4),
                        "bIn", args.get(5),
                        "bOut", args.get(6)
                );
            } else if (args.size() == 2) {
                return Map.of(
                        "img", args.getFirst(),
                        "color", args.get(1)
                );
            }
        }
        var minArgs = parameters.stream().filter(p -> p.required).count();
        var totalArgs = parameters.size();
        if (args.size() < minArgs || args.size() > totalArgs) {
            var sb = new StringBuilder();
            sb.append("Function '")
                    .append(name());
            if (minArgs == totalArgs) {
                sb.append("' expects ").append(totalArgs).append(" argument")
                        .append(totalArgs > 1 ? "s" : "").append(": ");
            } else {
                sb.append("' expects between ")
                        .append(minArgs)
                        .append(" and ")
                        .append(totalArgs)
                        .append(" arguments: ");
            }
            for (int i = 0; i < parameters.size(); i++) {
                var parameter = parameters.get(i);
                if (!parameter.required()) {
                    sb.append("[");
                }
                sb.append(parameter.name());
                if (parameter.description != null) {
                    sb.append(": (").append(parameter.description).append(")");
                }
                if (!parameter.required()) {
                    sb.append("]");
                }
                if (i < parameters.size() - 1) {
                    sb.append(", ");
                }
            }
            throw new IllegalArgumentException(sb.toString());
        }
        return IntStream.range(0, args.size())
                .mapToObj(i -> new Object() {
                    final String name = parameters.get(i).name();
                    final Object value = args.get(i);
                })
                .collect(Collectors.toMap(i -> i.name, i -> i.value, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public Set<String> getAllParameterNames() {
        return parameters.stream().map(Parameter::name).collect(Collectors.toSet());
    }

    private record Parameter(
            String name,
            String description,
            boolean required
    ) {
        private static final Parameter SPREAD_LIST = new Parameter("list", "list", true);
        private static final Parameter IMG = new Parameter("img", "image", true);
        private static final Parameter ANY = new Parameter("v", "value", true);
    }
}
