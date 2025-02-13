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

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public class I18N {
    private static final Map<String, ResourceBundle> CACHED_BUNDLES = new ConcurrentHashMap<>();

    private I18N() {

    }

    public static FXMLLoader fxmlLoader(Class<?> clazz, String resourceName) {
        try {
            var bundle = ResourceBundle.getBundle(clazz.getPackageName() + "." + resourceName, Locale.getDefault());
            return new FXMLLoader(clazz.getResource(resourceName + ".fxml"), bundle);
        } catch (MissingResourceException ex) {
            return new FXMLLoader(clazz.getResource(resourceName + ".fxml"));
        }
    }

    public static String string(Class<?> clazz, String resourceName, String label) {
        try {
            var baseName = clazz.getPackageName() + "." + resourceName;
            var bundle = CACHED_BUNDLES.computeIfAbsent(baseName, _ -> ResourceBundle.getBundle(baseName, Locale.getDefault()));
            return bundle.getString(label);
        } catch (MissingResourceException ex) {
            return "";
        }
    }

}
