#
# Copyright 2023-2023 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

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
