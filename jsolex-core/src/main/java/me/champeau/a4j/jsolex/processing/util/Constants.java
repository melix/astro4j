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
package me.champeau.a4j.jsolex.processing.util;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Constants {
    public static final float MAX_PIXEL_VALUE = 65535f;
    public static final String TYPE_RAW = "raw";
    public static final String TYPE_DEBUG = "debug";
    public static final String TYPE_CUSTOM = "custom";
    public static final String TYPE_PROCESSED = "processed";
    public static final double DEFAULT_CONTINUUM_SHIFT = 15;

    private static final ResourceBundle BUNDLE;

    static {
        ResourceBundle bundle;
        try {
            bundle = ResourceBundle.getBundle(Constants.class.getPackageName() + "." + "messages", LocaleUtils.getConfiguredLocale());
        } catch (MissingResourceException ex) {
            bundle = null;
        }
        BUNDLE = bundle;
    }

    private Constants() {

    }

    public static String message(String property) {
        if (!BUNDLE.containsKey(property)) {
            return property;
        }
        return BUNDLE.getString(property);
    }

}
