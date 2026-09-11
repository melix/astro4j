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

import time
import json
import clr
clr.AddReference("System")
clr.AddReference("System.Drawing")
from System import Array, Byte
from System.Net import WebClient, WebException
from System.IO import StreamReader
from System.Runtime.InteropServices import Marshal
from System.Text import Encoding
from System.Drawing import Color, Pen, SolidBrush, Font, PointF
from System.Drawing.Drawing2D import DashStyle

# ── Parameters ───────────────────────────────────────────────────────────────
JSOLEX_URL = "http://localhost:9122"  # address of the JSol'Ex embedded server
INTERVAL_SECONDS = 0.5                # time between two identifications
AVERAGED_FRAMES = 4                   # frames averaged by JSol'Ex before identifying
PIXEL_SIZE = None                     # micrometers; None uses the JSol'Ex observation details
BINNING = None                        # camera binning; None reads it from SharpCap
INSTRUMENT = None                     # name of the spectroheliograph in JSol'Ex; None uses the observation details
DUMP_FRAMES = False                   # True to have JSol'Ex save every frame it receives, to replay a bad identification
LABEL_FONT = Font("Arial", 11)
LABEL_WIDTH = 110                     # room kept for a label inside the right edge
LABEL_HEIGHT = 18
CURVE_STEP = 8                        # columns between two points of a drawn line

# The live view of a mono camera is a greyscale bitmap, on which any colour comes out
# grey: what is readable on it is contrast, so everything is drawn in white over a black
# outline, and a guessed centre line is told from a firm one by being dashed.
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
    try:
        reader = StreamReader(exception.Response.GetResponseStream())
        try:
            body = json.loads(reader.ReadToEnd())
        finally:
            reader.Dispose()
        return body.get("error") or body.get("message") or exception.Message
    except:
        return exception.Message

def identify(data, width, height, pixel_format):
    client = WebClient()
    client.Headers["Content-Type"] = "application/octet-stream"
    try:
        response = client.UploadData(identify_url(width, height, pixel_format), "POST", data)
        return json.loads(Encoding.UTF8.GetString(response))
    except WebException as e:
        print("  JSol'Ex error: {}".format(read_error(e)))
        return None
    finally:
        client.Dispose()

def reset_identification():
    client = WebClient()
    try:
        client.UploadString(JSOLEX_URL + "/api/spectrum/identify", "DELETE", "")
    except WebException as e:
        print("  Unable to reach JSol'Ex at {}: {}".format(JSOLEX_URL, read_error(e)))
        return False
    finally:
        client.Dispose()
    return True

# ── Overlay ──────────────────────────────────────────────────────────────────
state = {"result": None, "draw_error": False, "draws": 0}

def line_label(line):
    wavelength = "{:.2f}".format(line["wavelength"])
    if line.get("name"):
        return "{} {}".format(line["name"], wavelength)
    return wavelength

def status_text(result):
    anchor = result["anchor"]
    # The lead over the best competing line is what says whether the answer was settled:
    # a lead near zero means several lines explain the window just as well.
    return "JSol'Ex: {}  score {:.2f}  lead {:.2f}  confidence {} ({} frames)".format(
        line_label(anchor), anchor["score"], anchor["margin"], result["confidence"], result["streak"])

def draw_curve(graphics, columns, ys, width, dashed):
    """Draws a curve as white ink over a black outline, segment by segment with plain
    floats: the way IronPython types a .NET array is what made DrawLines unresolvable."""
    for color, pen_width, dash in ((OUTLINE_COLOR, width + OUTLINE_WIDTH, False), (INK_COLOR, width, dashed)):
        pen = Pen(color, pen_width)
        try:
            if dash:
                pen.DashStyle = DashStyle.Dash
            for i in range(len(columns) - 1):
                graphics.DrawLine(pen, float(columns[i]), ys[i], float(columns[i + 1]), ys[i + 1])
        finally:
            pen.Dispose()

def draw_text(graphics, text, x, y):
    """Draws text as white ink over a black outline, the outline being the text itself
    drawn one pixel away in every direction."""
    outline = SolidBrush(OUTLINE_COLOR)
    ink = SolidBrush(INK_COLOR)
    try:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            graphics.DrawString(text, LABEL_FONT, outline, PointF(x + dx, y + dy))
        graphics.DrawString(text, LABEL_FONT, ink, PointF(x, y))
    finally:
        outline.Dispose()
        ink.Dispose()

def draw_overlay(graphics, result):
    polynomial = result["polynomial"]
    a, b, c, d = polynomial["a"], polynomial["b"], polynomial["c"], polynomial["d"]
    left = result["leftBorder"]
    right = result["rightBorder"]
    anchor_wavelength = result["anchor"]["wavelength"]
    # A guess is dashed: it is the most likely line, not an answer.
    guessed = result["confidence"] == "NONE"
    # Labels sit just inside the frame: drawn past its edge, which is where the right
    # border lands when the whole width carries spectrum, they are simply clipped away.
    label_x = float(min(right + 4, max(0, result["width"] - LABEL_WIDTH)))
    label_limit = result["height"] - LABEL_HEIGHT
    columns = list(range(left, right, CURVE_STEP))
    if columns and columns[-1] != right - 1:
        columns.append(right - 1)
    # The centre line is what the observer is looking for, so it is drawn even when it is
    # not among the lines matched to the reference spectrum, where it sits by construction.
    lines = list(result["lines"])
    if not any(abs(line["wavelength"] - anchor_wavelength) < 0.01 for line in lines):
        lines.append({"wavelength": anchor_wavelength, "name": result["anchor"].get("name"), "pixelShift": 0.0})
    # A curve needs two points: a window one column wide only gets the status line.
    for line in lines if len(columns) > 1 else []:
        shift = line["pixelShift"]
        is_anchor = abs(line["wavelength"] - anchor_wavelength) < 0.01
        ys = [float(a * x * x * x + b * x * x + c * x + d + shift) for x in columns]
        draw_curve(graphics, columns, ys, 2 if is_anchor else 1, is_anchor and guessed)
        label_y = min(max(0.0, ys[-1] - 8), float(label_limit))
        draw_text(graphics, line_label(line), label_x, label_y)
    draw_text(graphics, status_text(result), 4.0, 4.0)

def on_before_display(sender, args):
    # Counted before anything else: a count which stays at zero says the live view never
    # asked for the overlay, which is a different problem from one which is not drawn.
    state["draws"] += 1
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
        # Never take down the live view because of a drawing error, but do say what went
        # wrong: a silent handler leaves an empty overlay with nothing to go on. Once is
        # enough, this runs on every frame which is displayed.
        if not state["draw_error"]:
            state["draw_error"] = True
            print("  Overlay could not be drawn: {}".format(e))

# ── Main loop ────────────────────────────────────────────────────────────────
def run():
    if not reset_identification():
        return
    print("Identifying spectral lines with JSol'Ex at {} (Ctrl+C to stop)".format(JSOLEX_URL))
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
                        # The height of the window is how much spectrum is compared with
                        # the reference, which is what decides whether a line can be told
                        # from all the others, so it is worth having in the log.
                        print("  {}  {} lines  {}x{} window  {} ms  {} overlay draws".format(
                            status_text(result), len(result["lines"]), width, height,
                            result["durationMillis"], state["draws"]))
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

run()
