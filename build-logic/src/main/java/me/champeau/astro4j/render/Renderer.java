package me.champeau.astro4j.render;

@FunctionalInterface
public interface Renderer<T> {
    String render(T object);
}
