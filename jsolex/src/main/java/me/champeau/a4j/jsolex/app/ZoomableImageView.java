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
package me.champeau.a4j.jsolex.app;

import javafx.scene.image.ImageView;

public class ZoomableImageView extends ImageView {
    public ZoomableImageView() {
        super();
        setOnScroll(event -> {
            double deltaY = event.getDeltaY();
            if (deltaY != 0) {
                double zoomFactor = 0.95;
                double scaleX = getScaleX();
                double scaleY = getScaleY();
                if (deltaY < 0) {
                    setScaleX(scaleX * zoomFactor);
                    setScaleY(scaleY * zoomFactor);
                } else {
                    setScaleX(scaleX / zoomFactor);
                    setScaleY(scaleY / zoomFactor);
                }
            }
            event.consume();
        });
    }
}
