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
package me.champeau.a4j.jsolex.processing.stats;

public class DefaultImageStatsComputer implements ImageStatsComputer {
    @Override
    public ChannelStats[] computeStats(double[] imageData, int width, int height) {
        int channelSize = width * height;
        int nbOfChannels = imageData.length / channelSize;
        ChannelStats[] channelStats = new ChannelStats[nbOfChannels];
        for (int i = 0; i < nbOfChannels; i++) {
            int[] histogram = new int[256];
            int[] cumulativeHistogram = new int[256];
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            double mean = 0;
            for (int j = 0; j < imageData.length; j += nbOfChannels) {
                double value = imageData[j + i];
                int index = (int) value;
                mean = mean + (value - mean) / ((double) j / nbOfChannels + 1);
                histogram[index]++;
                if (value < min) {
                    min = value;
                }
                if (value > max) {
                    max = value;
                }
            }
            for (int j = 0; j < 256; j++) {
                cumulativeHistogram[j] = histogram[j];
                if (j > 0) {
                    cumulativeHistogram[j] += cumulativeHistogram[j - 1];
                }
            }
            double stddev = 0;
            for (int j = 0; j < imageData.length; j += nbOfChannels) {
                double value = imageData[j + i];
                stddev += (value - mean) * (value - mean);
            }
            stddev = Math.sqrt(stddev / ((double) imageData.length / nbOfChannels));
            channelStats[i] = new ChannelStats(min, max, mean, stddev, histogram, cumulativeHistogram);
        }
        return channelStats;
    }
}
