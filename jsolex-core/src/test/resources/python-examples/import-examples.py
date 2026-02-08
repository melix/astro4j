# tag::global-variable[]
# As a global variable (inline scripts)
img = jsolex.load("image.fits")
# end::global-variable[]

# tag::import-statement[]
import jsolex
img = jsolex.load("image.fits")
# end::import-statement[]

# tag::from-import[]
from jsolex import load, sharpen, getVariable
img = load("image.fits")
# end::from-import[]
