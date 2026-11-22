/*
 * Copyright 2026 the original author or authors.
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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orders image titles alphabetically, comparing the numbers they contain by value, so
 * that "Image 2" comes before "Image 10".
 */
final class TitleOrder implements Comparator<String> {
    private final Collator collator = Collator.getInstance();

    @Override
    public int compare(String first, String second) {
        var firstParts = split(first);
        var secondParts = split(second);
        for (int i = 0; i < Math.min(firstParts.size(), secondParts.size()); i++) {
            var a = firstParts.get(i);
            var b = secondParts.get(i);
            var result = isNumber(a) && isNumber(b) ? compareNumbers(a, b) : collator.compare(a, b);
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(firstParts.size(), secondParts.size());
    }

    private static List<String> split(String title) {
        var parts = new ArrayList<String>();
        int start = 0;
        for (int i = 1; i <= title.length(); i++) {
            if (i == title.length() || Character.isDigit(title.charAt(i)) != Character.isDigit(title.charAt(i - 1))) {
                parts.add(title.substring(start, i));
                start = i;
            }
        }
        return parts;
    }

    private static boolean isNumber(String part) {
        return Character.isDigit(part.charAt(0));
    }

    private static int compareNumbers(String a, String b) {
        var first = stripLeadingZeros(a);
        var second = stripLeadingZeros(b);
        if (first.length() != second.length()) {
            return Integer.compare(first.length(), second.length());
        }
        return first.compareTo(second);
    }

    private static String stripLeadingZeros(String number) {
        int i = 0;
        while (i < number.length() - 1 && number.charAt(i) == '0') {
            i++;
        }
        return number.substring(i);
    }
}
