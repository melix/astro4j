# tag::complete-pipeline[]
import jsolex

# Get input image
img = jsolex.getVariable("continuum")
sharpen_amount = jsolex.getVariable("SharpenAmount")
threshold = jsolex.getVariable("WidthThreshold")

# Check image dimensions and adapt processing
width = jsolex.width(img)
height = jsolex.height(img)
print(f"Image size: {width}x{height}")

# Build processing pipeline based on image size
if width < threshold:
    # Small image: apply rescaling first
    img = jsolex.rescale(img, 2.0)
    img = jsolex.sharpen(img, sharpen_amount * 0.5)
else:
    # Large image: full processing
    img = jsolex.clahe(img, 8, 2.0)
    img = jsolex.sharpen(img, sharpen_amount)

# Final touches
img = jsolex.call("AUTOCROP", {"img": img})

result = img
# end::complete-pipeline[]
