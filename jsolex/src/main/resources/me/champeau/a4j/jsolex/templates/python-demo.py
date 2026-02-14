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
# meta:title = "Python Demo"
# meta:title:fr = "Demo Python"
# meta:author = "JSol'Ex"
# meta:version = "1.0"
#
# param:gamma:type = number
# param:gamma:default = 1.2
# param:gamma:min = 0.1
# param:gamma:max = 3.0
# param:gamma:name = "Gamma"
# param:gamma:name:fr = "Gamma"
# param:gamma:description = "Gamma correction for contrast adjustment"
# param:gamma:description:fr = "Correction gamma pour l'ajustement du contraste"
#
# param:sharpen_kernel:type = number
# param:sharpen_kernel:default = 3
# param:sharpen_kernel:min = 0
# param:sharpen_kernel:max = 15
# param:sharpen_kernel:name = "Sharpen Kernel"
# param:sharpen_kernel:name:fr = "Noyau d'accentuation"
# param:sharpen_kernel:description = "Kernel size for sharpening (0 = none)"
# param:sharpen_kernel:description:fr = "Taille du noyau d'accentuation (0 = aucun)"

import jsolex

# Get the image at pixel shift 0
img = jsolex.funcs.img(0)

# Get parameter values with defaults
gamma = jsolex.getVariable("gamma", 1.2)
kernel = jsolex.getVariable("sharpen_kernel", 3)

# Apply gamma correction
processed = jsolex.funcs.auto_contrast(img, gamma)

# Apply sharpening if kernel > 0
if kernel > 0:
    processed = jsolex.funcs.sharpen(processed, kernel)

# Emit the result to the UI
jsolex.emit(processed, "Python Demo Result", name="python_demo")
