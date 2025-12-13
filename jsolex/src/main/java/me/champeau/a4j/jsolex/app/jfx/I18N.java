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
package me.champeau.a4j.jsolex.app.jfx;

import javafx.fxml.FXMLLoader;
import me.champeau.a4j.jsolex.processing.util.LocaleUtils;

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internationalization utility for loading FXML and resource bundles.
 */
public class I18N {
    private static final Map<String, ResourceBundle> CACHED_BUNDLES = new ConcurrentHashMap<>();

    private I18N() {

    }

    /**
     * Creates an FXML loader with the appropriate resource bundle.
     * @param clazz the class used to locate the FXML resource
     * @param resourceName the resource name without extension
     * @return an FXML loader configured with the resource bundle
     */
    public static FXMLLoader fxmlLoader(Class<?> clazz, String resourceName) {
        try {
            var bundle = fetchBundle(clazz.getPackageName() + "." + resourceName);
            return new FXMLLoader(clazz.getResource(resourceName + ".fxml"), bundle);
        } catch (MissingResourceException ex) {
            return new FXMLLoader(clazz.getResource(resourceName + ".fxml"));
        }
    }

    /**
     * Gets a localized string from a resource bundle.
     * @param clazz the class used to locate the resource bundle
     * @param resourceName the resource bundle name
     * @param label the string key
     * @return the localized string, or empty string if not found
     */
    public static String string(Class<?> clazz, String resourceName, String label) {
        try {
            var baseName = clazz.getPackageName() + "." + resourceName;
            var bundle = fetchBundle(baseName);
            return bundle.getString(label);
        } catch (MissingResourceException ex) {
            return "";
        }
    }

    private static ResourceBundle fetchBundle(String baseName) {
        return CACHED_BUNDLES.computeIfAbsent(baseName, _ -> {
            if ("en".equals(LocaleUtils.getConfiguredLocale().toString())) {
                return ResourceBundle.getBundle(baseName, Locale.ROOT);
            }
            return ResourceBundle.getBundle(baseName, LocaleUtils.getConfiguredLocale());
        });
    }

}
