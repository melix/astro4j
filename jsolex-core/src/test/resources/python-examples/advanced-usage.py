# tag::return-value[]
# The value assigned to 'result' is returned to ImageMath
result = jsolex.sharpen(img, 1.5)
# end::return-value[]

# tag::return-dict[]
result = {
    "processed": img,
    "stats": {"min": min_val, "max": max_val},
    "quality": 0.95
}
# end::return-dict[]

# tag::call-user-function[]
img = jsolex.getVariable("continuum")
# Call user-defined ImageMath function
result = jsolex.callUserFunction("enhance", {"img": img})
# end::call-user-function[]

# tag::context-persistence[]
# First python() call
count = jsolex.getVariable("counter") or 0
jsolex.setVariable("counter", count + 1)
# end::context-persistence[]
