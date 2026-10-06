#
# Copyright 2026 the original author or authors.
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
# meta:title = "H-alpha animation (sliding window stack)"
# meta:title:fr = "Animation H-alpha (empilement glissant)"
# meta:author = "Cédric Champeau"
# meta:version = "1.0"
# meta:requires = "5.4.2"
# meta:description = "Produces a smooth animation of a series of H-alpha scans taken over a day. Must be executed in batch mode. Each image is processed with auto_contrast, then the images are sorted by date and stacked with a sliding window: the first frame of the animation is the stack of images 1 to N, the second the stack of images 2 to N+1, and so on. Before stacking, every scan is brought onto a common circle with correct_limb, which removes the slow wobbling of the disk caused by turbulence and tracking. Each window is then dedistorted against its own consensus reference, so that each frame is as sharp as a regular stack while consecutive frames share most of their images, which gives a smooth transition. Each frame is annotated with the observer and the time span it covers. A Doppler animation can be produced in addition, by stacking the two wings of the line with the same dedistortion as the line center."
# meta:description:fr = "Produit une animation fluide d'une série de scans H-alpha pris au cours d'une journée. Doit être exécuté en mode batch. Chaque image est traitée avec auto_contrast, puis les images sont triées par date et empilées avec une fenêtre glissante : la première image de l'animation est l'empilement des images 1 à N, la deuxième celui des images 2 à N+1, et ainsi de suite. Avant l'empilement, chaque scan est ramené sur un cercle commun avec correct_limb, ce qui supprime l'ondulation lente du disque due à la turbulence et au suivi. Chaque fenêtre est ensuite dé-déformée par rapport à sa propre référence par consensus, de sorte que chaque image de l'animation est aussi nette qu'un empilement classique, tandis que deux images consécutives partagent la plupart de leurs scans, ce qui donne une transition fluide. Chaque image est annotée avec l'observateur et la plage horaire qu'elle couvre. Une animation Doppler peut être produite en plus, en empilant les deux ailes de la raie avec la même dé-déformation que le centre de la raie."
#
# param:gamma:type = number
# param:gamma:default = 1.5
# param:gamma:min = 0.1
# param:gamma:max = 5.0
# param:gamma:name = "Contrast gamma"
# param:gamma:name:fr = "Gamma du contraste"
# param:gamma:description = "Gamma used by auto_contrast on each image before stacking."
# param:gamma:description:fr = "Gamma utilisé par auto_contrast sur chaque image avant l'empilement."
#
# param:window:type = number
# param:window:default = 5
# param:window:min = 1.0
# param:window:max = 1000.0
# param:window:name = "Images per frame"
# param:window:name:fr = "Images par vue"
# param:window:description = "Number of consecutive images stacked into each frame of the animation. More images give a cleaner frame but blur fast-changing details and shorten the animation."
# param:window:description:fr = "Nombre d'images consécutives empilées dans chaque vue de l'animation. Plus d'images donnent une vue plus propre mais floutent les détails qui évoluent vite et raccourcissent l'animation."
#
# param:step:type = number
# param:step:default = 1
# param:step:min = 1.0
# param:step:max = 1000.0
# param:step:name = "Window step"
# param:step:name:fr = "Pas de la fenêtre"
# param:step:description = "Number of images the window slides by between two frames. 1 uses every possible window."
# param:step:description:fr = "Nombre d'images dont la fenêtre se décale entre deux vues. 1 utilise toutes les fenêtres possibles."
#
# param:tileSize:type = number
# param:tileSize:default = 64
# param:tileSize:min = 16.0
# param:tileSize:max = 256.0
# param:tileSize:name = "Tile size"
# param:tileSize:name:fr = "Taille des tuiles"
# param:tileSize:description = "Size of the tiles used for dedistortion. Must be a power of 2."
# param:tileSize:description:fr = "Taille des tuiles utilisées pour la dé-déformation. Doit être une puissance de 2."
#
# param:crop:type = number
# param:crop:default = 1.2
# param:crop:min = 1.0
# param:crop:max = 3.0
# param:crop:name = "Cropping factor"
# param:crop:name:fr = "Facteur de rognage"
# param:crop:description = "Width of the frames, as a multiple of the solar diameter. Increase it to keep tall prominences."
# param:crop:description:fr = "Largeur des vues, en multiple du diamètre solaire. Augmentez-le pour conserver les protubérances les plus hautes."
#
# param:delay:type = number
# param:delay:default = 100
# param:delay:min = 20.0
# param:delay:max = 5000.0
# param:delay:name = "Frame duration (ms)"
# param:delay:name:fr = "Durée d'une vue (ms)"
# param:delay:description = "Time each frame of the animation is displayed, in milliseconds."
# param:delay:description:fr = "Durée d'affichage de chaque vue de l'animation, en millisecondes."
#
# param:doppler:type = choice
# param:doppler:default = "off"
# param:doppler:choices = "off,on"
# param:doppler:name = "Doppler animation"
# param:doppler:name:fr = "Animation Doppler"
# param:doppler:description = "Also produces a Doppler animation. This requires two more images per scan and three times more stacking work, which makes the processing noticeably longer."
# param:doppler:description:fr = "Produit également une animation Doppler. Cela demande deux images de plus par scan et trois fois plus d'empilements, ce qui rallonge sensiblement le traitement."
#
# param:dopplerShift:type = number
# param:dopplerShift:default = 0.5
# param:dopplerShift:min = 0.1
# param:dopplerShift:max = 5.0
# param:dopplerShift:name = "Doppler shift (Å)"
# param:dopplerShift:name:fr = "Décalage Doppler (Å)"
# param:dopplerShift:description = "Distance from the line center, in Ångströms, of the two wings combined into the Doppler image."
# param:dopplerShift:description:fr = "Distance au centre de la raie, en Ångströms, des deux ailes combinées dans l'image Doppler."
#
# param:saturation:type = number
# param:saturation:default = 1
# param:saturation:min = 0.0
# param:saturation:max = 5.0
# param:saturation:name = "Doppler saturation"
# param:saturation:name:fr = "Saturation Doppler"
# param:saturation:description = "Color saturation of the Doppler frames. 1 leaves the colors untouched."
# param:saturation:description:fr = "Saturation des couleurs des vues Doppler. 1 laisse les couleurs inchangées."
#
# output:ha:title = "H-alpha"
# output:ha:title:fr = "H-alpha"
# output:ha:description = "Per-image contrasted view, used as input for the stacks."
# output:ha:description:fr = "Vue contrastée de chaque image, utilisée en entrée des empilements."
#
# output:doppler_red:title = "Red wing"
# output:doppler_red:title:fr = "Aile rouge"
# output:doppler_red:description = "Per-image contrasted view of the wing displayed in red in the Doppler image."
# output:doppler_red:description:fr = "Vue contrastée de chaque image dans l'aile affichée en rouge dans l'image Doppler."
#
# output:doppler_blue:title = "Blue wing"
# output:doppler_blue:title:fr = "Aile bleue"
# output:doppler_blue:description = "Per-image contrasted view of the wing displayed in blue in the Doppler image."
# output:doppler_blue:description:fr = "Vue contrastée de chaque image dans l'aile affichée en bleu dans l'image Doppler."
#
# output:ha_anim:title = "H-alpha animation"
# output:ha_anim:title:fr = "Animation H-alpha"
# output:ha_anim:description = "Animation of the sliding-window stacks, annotated with the observer and the time span of each frame."
# output:ha_anim:description:fr = "Animation des empilements glissants, annotée avec l'observateur et la plage horaire de chaque vue."
#
# output:doppler_anim:title = "Doppler animation"
# output:doppler_anim:title:fr = "Animation Doppler"
# output:doppler_anim:description = "Doppler animation built from the same sliding windows as the H-alpha animation, receding regions in red and approaching ones in blue."
# output:doppler_anim:description:fr = "Animation Doppler construite à partir des mêmes fenêtres glissantes que l'animation H-alpha, régions qui s'éloignent en rouge et qui s'approchent en bleu."

import math

import jsolex

f = jsolex.funcs


def param(name):
    return jsolex.getVariable(name)


def doppler_enabled():
    return param("doppler") == "on"


def doppler_switched():
    # Same rule as the built-in Doppler image: the configured setting wins, and the
    # automatic mode uses the curvature of the spectral line
    params = jsolex.getProcessParams()
    colors = params.spectrumParams().dopplerColors().name() if params is not None else "AUTO"
    if colors != "AUTO":
        return colors == "SWITCHED"
    coefficients = jsolex.getPolynomialCoefficients()
    return coefficients is not None and coefficients[1] > 0


def single():
    gamma = param("gamma")
    outputs.ha = f.auto_contrast(f.img(0), gamma)
    if doppler_enabled():
        shift = f.a2px(param("dopplerShift"))
        if doppler_switched():
            shift = -shift
        outputs.doppler_red = f.auto_contrast(f.img(shift), gamma)
        outputs.doppler_blue = f.auto_contrast(f.img(-shift), gamma)


def to_list(images):
    return [images[i] for i in range(len(images))]


def sorted_by_date(images):
    # Batch results arrive in completion order, the animation needs chronological order
    return to_list(f.sort(images, "date"))


def disk_radius(image):
    params = jsolex.getEllipseParams(image)
    return params["radius"] if params else None


def annotate(image, text):
    return f.draw_text(image, 32, 64, text, 76, "FFFFFF")


def prepare(images, radius, size):
    # Images are brought to the common geometry one at a time
    return [f.radius_rescale2(img=img, radius=radius, width=size, height=size) for img in images]


def window_of(images, start, end):
    # The consensus dedistortion returns its images sorted by file name: the wings must
    # follow the same order so that each one is warped with the maps of its own scan
    return to_list(f.sort(images[start:end], "file_name"))


def batch(results):
    window = int(param("window"))
    step = int(param("step"))
    tile_size = int(param("tileSize"))
    crop = param("crop")
    delay = param("delay")
    doppler = doppler_enabled()
    saturation = param("saturation")

    images = sorted_by_date(results.ha)
    count = len(images)
    if count < 2:
        print("Not enough images to stack")
        return
    window = min(window, count)

    red = sorted_by_date(results.doppler_red) if doppler else []
    blue = sorted_by_date(results.doppler_blue) if doppler else []
    if doppler and (len(red) != count or len(blue) != count):
        print("The Doppler wings do not match the line center images, Doppler animation skipped")
        doppler = False

    # Same radius and same framing for every image, including the wings, so that all
    # frames share the same geometry. The target is read from the metadata
    radii = [radius for radius in (disk_radius(img) for img in images) if radius is not None]
    if not radii:
        print("No solar disk found in the images")
        return
    radius = int(round(max(radii)))
    size = int(math.ceil(2 * radius * crop / 16)) * 16

    # The whole series is brought onto the same circle before stacking, so that the disk
    # does not wobble from one frame to the next. The wings receive the correction
    # measured on the line center
    center = prepare(images, radius, size)
    corrected = to_list(f.correct_limb(center))
    if doppler:
        red = to_list(f.correct_limb(img=prepare(red, radius, size), ref=center))
        blue = to_list(f.correct_limb(img=prepare(blue, radius, size), ref=center))
    dates = [f.video_datetime(img, "yyyy-MM-dd") for img in images]
    times = [f.video_datetime(img, "HH:mm") for img in images]

    frames = []
    doppler_frames = []
    # The window shrinks at the end of the series, but a single image is never stacked
    windows = [(start, min(start + window, count)) for start in range(0, count, step)]
    windows = [(start, end) for start, end in windows if end - start >= 2]
    for index, (start, end) in enumerate(windows):
        print(f"Stacking frame {index + 1}/{len(windows)}: images {start + 1} to {end}")
        center_window = window_of(corrected, start, end)
        ref = f.stack_ref(center_window, "consensus")
        dedistorted = f.dedistort(ref=ref, img=center_window, ts=tile_size, sparse=1)
        text = f"%OBSERVER%\n{dates[start]}\n{times[start]} - {times[end - 1]} UTC ({end - start} images)"
        frames.append(annotate(f.stack_dedis(dedistorted), text))
        if doppler:
            # The wings are warped with the distortion maps computed on the line center,
            # so that the three stacks are perfectly aligned
            stacked_red = f.stack_dedis(f.dedistort(ref=dedistorted, img=window_of(red, start, end)))
            stacked_blue = f.stack_dedis(f.dedistort(ref=dedistorted, img=window_of(blue, start, end)))
            green = f.min([stacked_red, stacked_blue])
            doppler_image = f.saturate(f.rgb(stacked_red, green, stacked_blue), saturation)
            doppler_frames.append(annotate(doppler_image, text))

    outputs.ha_anim = f.anim(frames, delay)
    if doppler:
        outputs.doppler_anim = f.anim(doppler_frames, delay)
