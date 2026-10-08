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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import java.time.ZonedDateTime;

/**
 * Information about the video an image was produced from.
 *
 * @param serFileName the name of the SER file
 * @param parentDirName the name of the directory containing the SER file
 * @param dateTime the date of the first frame
 * @param width the width of the frames
 * @param height the number of frames
 * @param durationSeconds the time elapsed between the first and the last frame, or 0 if unknown
 */
public record SourceInfo(
    String serFileName,
    String parentDirName,
    ZonedDateTime dateTime,
    int width,
    int height,
    double durationSeconds
) {
    public SourceInfo(String serFileName, String parentDirName, ZonedDateTime dateTime, int width, int height) {
        this(serFileName, parentDirName, dateTime, width, height, 0);
    }
}
