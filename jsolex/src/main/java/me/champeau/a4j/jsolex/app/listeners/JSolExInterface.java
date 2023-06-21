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
package me.champeau.a4j.jsolex.app.listeners;

import javafx.scene.control.TabPane;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;

public interface JSolExInterface {
    ForkJoinContext getCpuExecutor();

    TabPane getMainPane();

    void showProgress();

    void hideProgress();

    void updateProgress(double progress, String text);
}
