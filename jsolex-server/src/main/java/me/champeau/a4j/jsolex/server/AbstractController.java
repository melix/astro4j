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
package me.champeau.a4j.jsolex.server;

import io.micronaut.views.ViewsRenderer;
import me.champeau.a4j.jsolex.processing.util.LocaleUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.Supplier;

/** Base class for web controllers providing common rendering and localization functionality. */
class AbstractController {
    private static final ResourceBundle BUNDLE;

    static {
        ResourceBundle bundle;
        try {
            bundle = ResourceBundle.getBundle(AbstractController.class.getPackageName() + "." + "messages", LocaleUtils.getConfiguredLocale());
        } catch (MissingResourceException ex) {
            bundle = null;
        }
        BUNDLE = bundle;
    }

    private final ViewsRenderer<Object, ?> viewsRenderer;

    /**
     * Creates a new abstract controller.
     * @param viewsRenderer the views renderer
     */
    AbstractController(ViewsRenderer<Object, ?> viewsRenderer) {
        this.viewsRenderer = viewsRenderer;
    }

    /**
     * Renders a view with the given model.
     * @param view the view name
     * @param modelSupplier supplier for the model
     * @param <T> the model type
     * @return rendered view as string
     */
    protected <T> String render(String view, Supplier<T> modelSupplier) {
        var writer = new StringWriter();
        try {
            viewsRenderer.render(view, modelSupplier.get(), null).writeTo(writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return writer.toString();
    }

    /**
     * Gets a localized string for the given property key.
     * @param property the property key
     * @return the localized string, or the key if not found
     */
    public static String localized(String property) {
        if (!BUNDLE.containsKey(property)) {
            return property;
        }
        return BUNDLE.getString(property);
    }
}
