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
