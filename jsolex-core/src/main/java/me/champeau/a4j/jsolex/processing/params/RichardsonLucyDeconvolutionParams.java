package me.champeau.a4j.jsolex.processing.params;

public record RichardsonLucyDeconvolutionParams(
    double radius,
    double sigma,
    int iterations
) {
    public RichardsonLucyDeconvolutionParams withRadius(double radius) {
        return new RichardsonLucyDeconvolutionParams(radius, sigma, iterations);
    }

    public RichardsonLucyDeconvolutionParams withSigma(double sigma) {
        return new RichardsonLucyDeconvolutionParams(radius, sigma, iterations);
    }

    public RichardsonLucyDeconvolutionParams withIterations(int iterations) {
        return new RichardsonLucyDeconvolutionParams(radius, sigma, iterations);
    }
}
