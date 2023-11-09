package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record TransformationHistory(
    List<String> transforms
) {
    public TransformationHistory(List<String> transforms) {
        this.transforms = Collections.unmodifiableList(transforms);
    }

    public TransformationHistory() {
        this(Collections.emptyList());
    }

    public TransformationHistory transform(String transform) {
        List<String> allTransforms = new ArrayList<>(transforms);
        allTransforms.add(transform);
        return new TransformationHistory(Collections.unmodifiableList(allTransforms));
    }

    public static ImageWrapper recordTransform(ImageWrapper image, String transform) {
        var history = (TransformationHistory) image.metadata().computeIfAbsent(
            TransformationHistory.class,
            unused -> new TransformationHistory()
        );
        image.metadata().put(TransformationHistory.class, history.transform(transform));
        return image;
    }
}
