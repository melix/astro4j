# tag::for-loop[]
# Process multiple images with a for loop
images = []
for shift in range(-5, 6):
    img = jsolex.call("IMG", {"shift": shift})
    img = jsolex.sharpen(img, 1.0 + abs(shift) * 0.1)
    images.append(img)
result = images
# end::for-loop[]

# tag::conditionals[]
img = jsolex.getVariable("continuum")
width = jsolex.width(img)

if width > 2000:
    # Large image: apply strong enhancement
    img = jsolex.sharpen(img, 2.0)
elif width > 1000:
    # Medium image: moderate enhancement
    img = jsolex.sharpen(img, 1.2)
else:
    # Small image: gentle enhancement
    img = jsolex.sharpen(img, 0.8)

result = img
# end::conditionals[]

# tag::exception-handling[]
try:
    img = jsolex.load("optional_file.fits")
except:
    img = jsolex.getVariable("fallback")
result = img
# end::exception-handling[]
