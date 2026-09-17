# JSol'Ex spectral line overlay for SharpCap
#
# Sends the live frames of the selected camera to a running JSol'Ex, which
# identifies the absorption lines visible in the capture window, and draws
# the lines with their names over the live view. Move the grating of your
# spectroheliograph and the overlay follows.
#
# Requirements:
#   - JSol'Ex is running with its embedded web server started (Tools menu).
#   - The spectroheliograph and the pixel size of the camera are configured
#     in the observation details of JSol'Ex, unless given below. The binning
#     is read from the camera.
#   - The camera uses one of the MONO, RGB or RAW colour spaces, with the
#     dispersion axis vertical and the slit horizontal, as in the SER files
#     JSol'Ex processes. Colour frames are averaged to mono by JSol'Ex.
#
# Frames are read from memory and sent as raw pixels: nothing is written to
# disk. Stop the script with Ctrl+C: the overlay is removed and the camera
# is left untouched.

import math
import time
import json
import clr
clr.AddReference("System")
clr.AddReference("System.Drawing")
clr.AddReference("System.Windows.Forms")
from System import Action, Array, Byte, Uri
from System.Net import WebException, WebRequest
from System.IO import StreamReader
from System.Runtime.InteropServices import Marshal
from System.Text import Encoding
from System.Threading import Thread, ThreadStart, ApartmentState
from System.Drawing import Color, Pen, SolidBrush, Font, PointF
from System.Drawing.Drawing2D import DashStyle
from System.Windows.Forms import Application, Form, FormBorderStyle, FormStartPosition, Label, NumericUpDown, RadioButton, TextBox

# ── Parameters ───────────────────────────────────────────────────────────────
JSOLEX_URL = "http://localhost:9122"  # address of the JSol'Ex embedded server
INTERVAL_SECONDS = 0.5                # time between two identifications
AVERAGED_FRAMES = 4                   # frames averaged by JSol'Ex before identifying
PIXEL_SIZE = None                     # micrometers; None uses the JSol'Ex observation details
BINNING = None                        # camera binning; None reads it from SharpCap
INSTRUMENT = None                     # name of the spectroheliograph in JSol'Ex; None uses the observation details
DUMP_FRAMES = False                   # True to have JSol'Ex save every frame it receives, to replay a bad identification
REQUEST_TIMEOUT_MS = 60000            # how long to wait for JSol'Ex to answer
MAX_LINES = 20                        # initial number of labelled lines, adjustable in the settings window
FONT_SIZE = 11                        # initial label size on screen, adjustable in the settings window
FONT_NAME = "Arial"
LABEL_WIDTH = 110                     # room kept for a label inside the right edge, at 100% zoom
CURVE_STEP = 8                        # columns between two points of a drawn line
TICK_LENGTH = 12                      # length of a ruler tick or of an unlabelled line mark, at 100% zoom
RULER_LABELS = 10                     # how many labels the ruler aims for over the height of the window
RULER_STEPS = (0.5, 1, 2, 5, 10, 20, 50, 100, 200, 500)
TARGET_MATCH_ANGSTROMS = 0.05         # how close an identified line must be to the searched wavelength to be it

# The live view is a greyscale bitmap, so the overlay is drawn in white over a black outline.
INK_COLOR = Color.White
OUTLINE_COLOR = Color.Black
OUTLINE_WIDTH = 2                     # extra width of the outline around a curve, in pixels

cam = SharpCap.SelectedCamera

# ── Frame capture ────────────────────────────────────────────────────────────
# Bytes per pixel of the colour spaces JSol'Ex understands, as SharpCap names them.
BYTES_PER_PIXEL = {
    "MONO8": 1, "MONO12": 2, "MONO16": 2,
    "RGB24": 3, "RGB32": 4, "RGB48": 6,
    "RAW8": 1, "RAW12": 2, "RAW16": 2,
}

def read_pixel_format():
    """The colour space of the camera, as SharpCap names it (MONO16, RGB24...)."""
    return str(cam.Controls.ColourSpace.Value).strip().upper()

def grab_frame(timeout=3.0):
    """Copies the next live frame into a byte array, straight from memory.

    Returns (bytes, width, height, pixel format) or None on timeout. Errors
    raised inside the handler are smuggled out through the closure cell and
    re-raised."""
    result = [None]

    def on_frame(sender, args):
        if result[0] is not None:
            return
        try:
            frame = args.Frame
            roi = cam.ROI
            width = int(roi.Width)
            height = int(roi.Height)
            pixel_format = read_pixel_format()
            bytes_per_pixel = BYTES_PER_PIXEL.get(pixel_format)
            if bytes_per_pixel is None:
                raise Exception("Unsupported colour space {}: switch the camera to one of {}".format(pixel_format, ", ".join(sorted(BYTES_PER_PIXEL.keys()))))
            expected = width * height * bytes_per_pixel
            size = int(frame.BufferSize)
            if size < expected:
                raise Exception("The frame buffer holds {} bytes but a {}x{} {} frame needs {}".format(size, width, height, pixel_format, expected))
            lease = frame.GetBufferLease()
            try:
                data = Array.CreateInstance(Byte, expected)
                Marshal.Copy(lease.Buffer, data, 0, expected)
            finally:
                lease.Dispose()
            result[0] = (data, width, height, pixel_format)
        except Exception as e:
            result[0] = ("error", str(e))

    cam.FrameCaptured += on_frame
    try:
        t0 = time.time()
        while result[0] is None:
            if time.time() - t0 > timeout:
                return None
            time.sleep(0.02)
    finally:
        try:
            cam.FrameCaptured -= on_frame
        except:
            pass
    if result[0][0] == "error":
        raise Exception(result[0][1])
    return result[0]

# ── Camera settings ──────────────────────────────────────────────────────────
def read_binning():
    """The camera binning as SharpCap reports it, or None when it cannot be read,
    in which case JSol'Ex considers both 1 and 2. Drivers report either an
    integer or a string such as "2x2"."""
    if BINNING is not None:
        return BINNING
    try:
        value = str(cam.Controls.Binning.Value).strip()
        digits = ""
        for char in value:
            if char.isdigit():
                digits += char
            else:
                break
        return int(digits) if digits else None
    except:
        return None

# ── JSol'Ex client ───────────────────────────────────────────────────────────
def identify_url(width, height, pixel_format):
    url = "{}/api/spectrum/identify?width={}&height={}&format={}&average={}".format(JSOLEX_URL, width, height, pixel_format, AVERAGED_FRAMES)
    if PIXEL_SIZE is not None:
        url += "&pixelSize={}".format(PIXEL_SIZE)
    binning = read_binning()
    if binning is not None:
        url += "&binning={}".format(binning)
    if INSTRUMENT is not None:
        url += "&instrument={}".format(INSTRUMENT)
    if DUMP_FRAMES:
        url += "&dump=true"
    return url

def read_error(exception):
    """The message reported by JSol'Ex, or the one of the failure itself when
    the body does not come from JSol'Ex, as for an error raised by the server."""
    message = getattr(exception, "Message", None) or str(exception)
    try:
        reader = StreamReader(exception.Response.GetResponseStream())
        try:
            body = json.loads(reader.ReadToEnd())
        finally:
            reader.Dispose()
        return body.get("error") or body.get("message") or message
    except:
        return message

def request(url, method, data):
    """Sends a request from a worker thread and waits for its outcome in short sleeps.

    Ctrl+C stops the script by aborting its thread. An abort landing inside a
    blocking web request leaves .NET with a failed task nobody observes, which
    SharpCap then reports as a scripting error once the script has stopped. So the
    script thread never blocks in the request: it sleeps, and an interrupt aborts
    the request so that the worker thread finishes at once."""
    web_request = WebRequest.Create(Uri(url))
    web_request.Method = method
    web_request.Timeout = REQUEST_TIMEOUT_MS
    # A request without a body, as the reset, must not touch the request stream at
    # all: an empty body still makes .NET send headers the server refuses.
    if data is not None:
        web_request.ContentType = "application/octet-stream"
        web_request.ContentLength = data.Length
    outcome = {}

    def work():
        try:
            if data is not None:
                stream = web_request.GetRequestStream()
                try:
                    stream.Write(data, 0, data.Length)
                finally:
                    stream.Dispose()
            response = web_request.GetResponse()
            try:
                reader = StreamReader(response.GetResponseStream())
                try:
                    outcome["body"] = reader.ReadToEnd()
                finally:
                    reader.Dispose()
            finally:
                response.Dispose()
        except WebException as e:
            outcome["error"] = e
        except Exception as e:
            outcome["error"] = e
        outcome["done"] = True

    worker = Thread(ThreadStart(work))
    worker.IsBackground = True
    worker.Start()
    try:
        while not outcome.get("done"):
            time.sleep(0.02)
    except KeyboardInterrupt:
        web_request.Abort()
        raise
    return outcome

def identify(data, width, height, pixel_format):
    outcome = request(identify_url(width, height, pixel_format), "POST", data)
    if "error" in outcome:
        print("  JSol'Ex error: {}".format(read_error(outcome["error"])))
        return None
    return json.loads(outcome["body"])

def reset_identification():
    """Asks JSol'Ex to forget the previous session. A failure is reported but does
    not stop the script: identification does not depend on it."""
    outcome = request(JSOLEX_URL + "/api/spectrum/identify", "DELETE", None)
    if "error" in outcome:
        print("  Unable to reset JSol'Ex at {}: {}".format(JSOLEX_URL, read_error(outcome["error"])))

# ── Settings ─────────────────────────────────────────────────────────────────
# Edited from the settings window, read by the overlay on every frame.
settings = {"mode": "all", "max_lines": MAX_LINES, "font_size": FONT_SIZE, "target": None}

def build_settings_form():
    form = Form(Text="JSol'Ex spectral lines", TopMost=True, Width=300, Height=230)
    form.FormBorderStyle = FormBorderStyle.FixedToolWindow
    form.StartPosition = FormStartPosition.CenterScreen

    all_lines = RadioButton(Text="Show the identified lines", Left=12, Top=12, Width=260, Checked=True)
    find_line = RadioButton(Text="Find a line", Left=12, Top=36, Width=260)
    form.Controls.Add(all_lines)
    form.Controls.Add(find_line)

    form.Controls.Add(Label(Text="Lines shown at most", Left=32, Top=66, Width=140))
    max_lines = NumericUpDown(Left=180, Top=62, Width=80, Minimum=1, Maximum=500, Value=MAX_LINES)
    form.Controls.Add(max_lines)

    form.Controls.Add(Label(Text=u"Wavelength to find (\u212b)", Left=32, Top=96, Width=140))
    target = TextBox(Left=180, Top=92, Width=80)
    form.Controls.Add(target)

    form.Controls.Add(Label(Text="Label size", Left=12, Top=136, Width=140))
    font_size = NumericUpDown(Left=180, Top=132, Width=80, Minimum=6, Maximum=72, Value=FONT_SIZE)
    form.Controls.Add(font_size)

    def mode_changed(sender, args):
        settings["mode"] = "find" if find_line.Checked else "all"

    def max_lines_changed(sender, args):
        settings["max_lines"] = int(max_lines.Value)

    def font_size_changed(sender, args):
        settings["font_size"] = int(font_size.Value)

    def target_changed(sender, args):
        try:
            settings["target"] = float(target.Text.strip().replace(",", "."))
        except ValueError:
            settings["target"] = None

    find_line.CheckedChanged += mode_changed
    max_lines.ValueChanged += max_lines_changed
    font_size.ValueChanged += font_size_changed
    target.TextChanged += target_changed
    return form

def open_settings_window():
    """Runs the settings window on its own thread, since the script thread is busy
    talking to JSol'Ex and Windows Forms needs a thread pumping messages."""
    def run_form():
        form = build_settings_form()
        state["form"] = form
        Application.Run(form)
        state["form"] = None

    thread = Thread(ThreadStart(run_form))
    thread.SetApartmentState(ApartmentState.STA)
    thread.IsBackground = True
    thread.Start()

def close_settings_window():
    form = state.get("form")
    if form is None:
        return
    try:
        form.Invoke(Action(form.Close))
    except:
        pass

# ── Overlay ──────────────────────────────────────────────────────────────────
state = {"result": None, "draw_error": False, "form": None}

def line_label(line):
    wavelength = "{:.2f}".format(line["wavelength"])
    if line.get("name"):
        return "{} {}".format(line["name"], wavelength)
    return wavelength

def status_text(result):
    anchor = result["anchor"]
    return "JSol'Ex: {}  score {:.2f}  lead {:.2f}  confidence {} ({} frames)".format(
        line_label(anchor), anchor["score"], anchor["margin"], result["confidence"], result["streak"])

def zoom_scale():
    """How many frame pixels one screen pixel covers: what is drawn on the frame is
    scaled by this so that it keeps its size on screen whatever the zoom."""
    try:
        zoom = float(SharpCap.ZoomPercent)
        if zoom > 0:
            return 100.0 / zoom
    except:
        pass
    return 1.0

class Ink(object):
    """The pens, brushes and font of one overlay, sized for the current zoom."""

    def __init__(self, scale):
        self.scale = scale
        self.font = Font(FONT_NAME, float(settings["font_size"] * scale))
        self.outline = SolidBrush(OUTLINE_COLOR)
        self.ink = SolidBrush(INK_COLOR)
        self.label_width = LABEL_WIDTH * scale
        self.label_height = self.font.Height
        self.tick = TICK_LENGTH * scale

    def dispose(self):
        self.font.Dispose()
        self.outline.Dispose()
        self.ink.Dispose()

    def polyline(self, graphics, xs, ys, width, dashed=False):
        for color, pen_width, dash in ((OUTLINE_COLOR, (width + OUTLINE_WIDTH) * self.scale, False), (INK_COLOR, width * self.scale, dashed)):
            pen = Pen(color, float(pen_width))
            try:
                if dash:
                    pen.DashStyle = DashStyle.Dash
                for i in range(len(xs) - 1):
                    graphics.DrawLine(pen, float(xs[i]), float(ys[i]), float(xs[i + 1]), float(ys[i + 1]))
            finally:
                pen.Dispose()

    def text(self, graphics, text, x, y):
        offset = max(1.0, self.scale)
        for dx, dy in ((-offset, 0), (offset, 0), (0, -offset), (0, offset)):
            graphics.DrawString(text, self.font, self.outline, PointF(x + dx, y + dy))
        graphics.DrawString(text, self.font, self.ink, PointF(x, y))

class Geometry(object):
    """Where things are in the frame: rows of the lines, wavelength of a row."""

    def __init__(self, result):
        polynomial = result["polynomial"]
        self.a, self.b, self.c, self.d = polynomial["a"], polynomial["b"], polynomial["c"], polynomial["d"]
        self.left = result["leftBorder"]
        self.right = result["rightBorder"]
        self.width = result["width"]
        self.height = result["height"]
        self.anchor = result["anchor"]["wavelength"]
        self.dispersion = result["angstromsPerPixel"]
        # The server reports rows in frame coordinates: when the frame is flipped, the
        # wavelength decreases as the row increases.
        self.direction = -1.0 if result.get("flipped") else 1.0
        self.columns = list(range(self.left, self.right, CURVE_STEP))
        if self.columns and self.columns[-1] != self.right - 1:
            self.columns.append(self.right - 1)

    def centre_row(self, x):
        return self.a * x * x * x + self.b * x * x + self.c * x + self.d

    def rows(self, shift):
        return [self.centre_row(x) + shift for x in self.columns]

    def shift_of(self, wavelength):
        return self.direction * (wavelength - self.anchor) / self.dispersion

    def wavelength_at(self, shift):
        return self.anchor + self.direction * shift * self.dispersion

    def label_x(self, ink):
        return float(min(self.right + 4 * ink.scale, max(0, self.width - ink.label_width)))

    def label_y(self, ink, row):
        return float(min(max(0.0, row - ink.label_height / 2), self.height - ink.label_height))

def select_lines(lines, max_lines, anchor_wavelength):
    """Keeps the centre line and spreads the remaining labels evenly over the window,
    each slot going to the nearest line, named and deeper lines being preferred."""
    if len(lines) <= max_lines:
        return lines
    ordered = sorted(lines, key=lambda line: line["pixelShift"])
    chosen = [line for line in ordered if abs(line["wavelength"] - anchor_wavelength) < 0.01][:1]
    slots = max_lines - len(chosen)
    if slots <= 0:
        return chosen
    low = ordered[0]["pixelShift"]
    high = ordered[-1]["pixelShift"]
    spacing = (high - low) / slots
    for i in range(slots):
        target = low + spacing * (i + 0.5)
        candidates = [line for line in ordered if line not in chosen]
        if not candidates:
            break
        chosen.append(min(candidates, key=lambda line: (
            abs(line["pixelShift"] - target) - (0.25 * spacing if line.get("name") else 0.0),
            -line.get("depth", 0.0))))
    return sorted(chosen, key=lambda line: line["pixelShift"])

def draw_line(graphics, ink, geometry, line, width, dashed, labelled):
    ys = geometry.rows(line["pixelShift"])
    if labelled:
        ink.polyline(graphics, geometry.columns, ys, width, dashed)
        ink.text(graphics, line_label(line), geometry.label_x(ink), geometry.label_y(ink, ys[-1]))
    else:
        x = geometry.columns[-1]
        ink.polyline(graphics, [x - ink.tick, x], [ys[-1], ys[-1]], width)

def draw_all_lines(graphics, ink, geometry, result):
    anchor_wavelength = geometry.anchor
    guessed = result["confidence"] == "NONE"
    lines = list(result["lines"])
    if not any(abs(line["wavelength"] - anchor_wavelength) < 0.01 for line in lines):
        lines.append({"wavelength": anchor_wavelength, "name": result["anchor"].get("name"), "pixelShift": 0.0, "depth": 1.0})
    labelled = select_lines(lines, settings["max_lines"], anchor_wavelength)
    for line in lines:
        is_anchor = abs(line["wavelength"] - anchor_wavelength) < 0.01
        draw_line(graphics, ink, geometry, line, 2 if is_anchor else 1, is_anchor and guessed, line in labelled)
    ink.text(graphics, status_text(result), 4.0, 4.0)

def ruler_step(span):
    for step in RULER_STEPS:
        if span / step <= RULER_LABELS:
            return step
    return RULER_STEPS[-1]

def draw_ruler(graphics, ink, geometry):
    x = geometry.columns[-1]
    ink.polyline(graphics, [x, x], [0, geometry.height], 1)
    top = geometry.wavelength_at(-geometry.centre_row(x))
    bottom = geometry.wavelength_at(geometry.height - geometry.centre_row(x))
    low, high = min(top, bottom), max(top, bottom)
    step = ruler_step(high - low)
    wavelength = math.ceil(low / step) * step
    while wavelength <= high:
        row = geometry.centre_row(x) + geometry.shift_of(wavelength)
        ink.polyline(graphics, [x - ink.tick, x], [row, row], 1)
        ink.text(graphics, "{:g}".format(wavelength), geometry.label_x(ink), geometry.label_y(ink, row))
        wavelength += step

def draw_find_line(graphics, ink, geometry, result):
    draw_ruler(graphics, ink, geometry)
    target = settings["target"]
    status = "JSol'Ex: centre {}".format(line_label(result["anchor"]))
    if target is None:
        status += "  enter the wavelength to find in the settings window"
    else:
        line = {"wavelength": target, "pixelShift": geometry.shift_of(target)}
        # The searched wavelength need not be a line, so it is placed from the
        # dispersion; when it is one, the measured position is exact and the name known.
        for found in result["lines"]:
            if abs(found["wavelength"] - target) < TARGET_MATCH_ANGSTROMS:
                line = found
        shift = line["pixelShift"]
        row = geometry.centre_row(geometry.columns[-1]) + shift
        if 0 <= row < geometry.height:
            draw_line(graphics, ink, geometry, line, 2, False, True)
            status += "  {:.2f} is in the window".format(target)
        else:
            beyond = (-row if row < 0 else row - geometry.height + 1) * geometry.dispersion
            status += u"  {:.2f} is {:.1f} \u212b beyond the {} edge".format(target, beyond, "top" if row < 0 else "bottom")
    ink.text(graphics, status, 4.0, 4.0)

def draw_overlay(graphics, result):
    geometry = Geometry(result)
    ink = Ink(zoom_scale())
    try:
        if len(geometry.columns) < 2:
            ink.text(graphics, status_text(result), 4.0, 4.0)
        elif settings["mode"] == "find":
            draw_find_line(graphics, ink, geometry, result)
        else:
            draw_all_lines(graphics, ink, geometry, result)
    finally:
        ink.dispose()

def on_before_display(sender, args):
    result = state["result"]
    if result is None:
        return
    try:
        bitmap = args.Frame.GetDrawableBitmap()
        try:
            graphics = bitmap.GetGraphics()
            try:
                draw_overlay(graphics, result)
            finally:
                graphics.Dispose()
        finally:
            bitmap.Dispose()
    except Exception as e:
        # A drawing error must not take down the live view, and is reported once.
        if not state["draw_error"]:
            state["draw_error"] = True
            print("  Overlay could not be drawn: {}".format(e))

# ── Main loop ────────────────────────────────────────────────────────────────
def run():
    reset_identification()
    print("Identifying spectral lines with JSol'Ex at {} (Ctrl+C to stop)".format(JSOLEX_URL))
    open_settings_window()
    cam.BeforeFrameDisplay += on_before_display
    try:
        while True:
            grabbed = grab_frame()
            if grabbed is None:
                print("  No frame received from the camera")
            else:
                data, width, height, pixel_format = grabbed
                result = identify(data, width, height, pixel_format)
                if result is not None:
                    if result["identified"]:
                        state["result"] = result
                        print("  {}  {} lines  {}x{} window  {} ms".format(
                            status_text(result), len(result["lines"]), width, height, result["durationMillis"]))
                        if result.get("dumped"):
                            print("  frame saved as {}".format(result["dumped"]))
                    elif result["spectrumFound"]:
                        print("  Spectrum found but no line identified yet")
                    else:
                        print("  No spectrum in the frame")
            time.sleep(INTERVAL_SECONDS)
    except KeyboardInterrupt:
        print("Stopped")
    finally:
        try:
            cam.BeforeFrameDisplay -= on_before_display
        except:
            pass
        state["result"] = None
        close_settings_window()

run()
