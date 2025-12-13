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

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.views.ModelAndView;
import io.micronaut.views.View;
import io.micronaut.views.ViewsRenderer;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;

import java.io.File;
import java.util.Map;

/** Main HTTP controller for the JSol'Ex web interface. */
@Controller("/")
public class MainController extends AbstractController {
    private final ImagesStore imagesStore;
    private final SharedContext sharedContext;

    /**
     * Creates a new main controller.
     * @param imagesStore the image store
     * @param viewsRenderer the views renderer
     * @param sharedContext the shared context
     */
    public MainController(ImagesStore imagesStore,
                          ViewsRenderer<Object, ?> viewsRenderer,
                          SharedContext sharedContext) {
        super(viewsRenderer);
        this.imagesStore = imagesStore;
        this.sharedContext = sharedContext;
    }

    /**
     * Serves the index page.
     * @return HTTP response with the index view
     */
    @Get("/")
    @View("index")
    public HttpResponse<?> index() {
        return HttpResponse.ok();
    }

    /**
     * Serves an image by its ID.
     * @param id the image ID
     * @return HTTP response with the image file or not found
     */
    @Get("/image/{id}")
    HttpResponse<File> image(Long id) {
        return imagesStore.findImage(id).map(image -> {
            var path = image.path();
            var file = new File(path);
            if (file.exists()) {
                return HttpResponse.ok(file)
                    .contentType(MediaType.IMAGE_JPEG);
            }
            return HttpResponse.<File>notFound();
        }).orElseGet(HttpResponse::notFound);
    }

    /**
     * Serves the menu view.
     * @return model and view for the menu
     */
    @Get("/views/menu")
    public ModelAndView<?> view() {
        var params = sharedContext.get(ProcessParams.class);
        var observer = params.observationDetails().observer();
        return new ModelAndView<>("menu", Map.of(
            "version", VersionUtil.getFullVersion(),
            "observer", observer == null ? "" : String.format(localized("observer"), observer))
        );
    }

}
