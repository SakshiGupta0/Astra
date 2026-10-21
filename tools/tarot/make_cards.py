#!/usr/bin/env python3
"""ASTRA deck (astra-deck-1): 78 original card designs plus the card back, drawn as celestial line art
per the ASTRA Visual Identity guide (midnight surfaces, champagne-gold line work, lavender depth).

One source, two outputs:
  app/src/main/res/drawable/tarot_<id>.xml   Android vector drawables used by the app
  build/tarot/<id>.svg + build/tarot/sheet.html   previews for review

Card text (numeral and name) is drawn by the app in Compose, in the top and bottom bands left clear here.
Viewport 140 x 240. Emblem area roughly x 26..114, y 48..184, centred at (70, 114).
Usage: python3 tools/tarot/make_cards.py
"""
import math, os, random, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app/src/main/res/drawable")
PREV = os.path.join(ROOT, "build/tarot")

# Palette (design guide tokens)
FACE = "#0E1530"      # between Midnight #080F24 and Indigo #181648
BACK = "#181648"
GOLD = "#F7D794"
LAV = "#A78BFA"
MOON = "#F5F3FF"
PURPLE = "#602D89"
SILVER = "#C9CDE0"

W, H = 140, 240
CX, CY = 70, 114


def f(x):
    s = f"{x:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


class Art:
    def __init__(self):
        self.items = []  # (d, stroke, width, fill, stroke_alpha, fill_alpha)

    def path(self, d, stroke=GOLD, w=1.2, fill=None, sa=1.0, fa=1.0):
        self.items.append((d, stroke, w, fill, sa, fa))

    def circle(self, cx, cy, r, **k):
        self.path(f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0Z", **k)

    def line(self, x1, y1, x2, y2, **k):
        self.path(f"M{f(x1)},{f(y1)}L{f(x2)},{f(y2)}", **k)

    def poly(self, pts, closed=True, **k):
        d = "M" + "L".join(f"{f(x)},{f(y)}" for x, y in pts) + ("Z" if closed else "")
        self.path(d, **k)

    def star(self, cx, cy, ro, ri, n=5, rot=-90, **k):
        pts = []
        for i in range(2 * n):
            r = ro if i % 2 == 0 else ri
            a = math.radians(rot + i * 180 / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        self.poly(pts, **k)

    def arc(self, cx, cy, r, a0, a1, **k):
        """Arc from angle a0 to a1 (degrees, clockwise on screen)."""
        x0, y0 = cx + r * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0))
        x1, y1 = cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1))
        large = 1 if (a1 - a0) % 360 > 180 else 0
        self.path(f"M{f(x0)},{f(y0)}A{f(r)},{f(r)} 0 {large},1 {f(x1)},{f(y1)}", **k)

    def rays(self, cx, cy, r0, r1, n, rot=0, **k):
        for i in range(n):
            a = math.radians(rot + i * 360 / n)
            self.line(cx + r0 * math.cos(a), cy + r0 * math.sin(a), cx + r1 * math.cos(a), cy + r1 * math.sin(a), **k)

    def dot(self, x, y, r=0.9, color=GOLD, a=1.0):
        self.circle(x, y, r, stroke=None, w=0, fill=color, fa=a)

    def crescent(self, cx, cy, r, phase=0.45, rot=0, **k):
        """Crescent: outer circle arc and inner arc; rot rotates the opening (0 = opening to the right)."""
        a = math.radians(rot)
        # endpoints at +/- 90 deg from opening direction
        ux, uy = math.cos(a), math.sin(a)
        px, py = -uy, ux
        p1 = (cx + px * r, cy + py * r)
        p2 = (cx - px * r, cy - py * r)
        ri = r * (1 + phase)
        self.path(f"M{f(p1[0])},{f(p1[1])}A{f(r)},{f(r)} 0 1,1 {f(p2[0])},{f(p2[1])}A{f(ri)},{f(ri)} 0 0,0 {f(p1[0])},{f(p1[1])}Z", **k)

    def lemniscate(self, cx, cy, size, **k):
        w = size
        h = w * 0.33
        self.path(f"M{f(cx)},{f(cy)}C{f(cx + w * .35)},{f(cy - h)} {f(cx + w)},{f(cy - h)} {f(cx + w)},{f(cy)}"
                  f"C{f(cx + w)},{f(cy + h)} {f(cx + w * .35)},{f(cy + h)} {f(cx)},{f(cy)}"
                  f"C{f(cx - w * .35)},{f(cy - h)} {f(cx - w)},{f(cy - h)} {f(cx - w)},{f(cy)}"
                  f"C{f(cx - w)},{f(cy + h)} {f(cx - w * .35)},{f(cy + h)} {f(cx)},{f(cy)}Z", **k)

    def wave(self, x0, x1, y, amp=2.0, n=6, **k):
        step = (x1 - x0) / n
        d = f"M{f(x0)},{f(y)}"
        for i in range(n):
            xa = x0 + step * i
            d += f"Q{f(xa + step / 2)},{f(y - amp if i % 2 == 0 else y + amp)} {f(xa + step)},{f(y)}"
        self.path(d, **k)


# ---------------------------------------------------------------- suit symbols (unit ~24 tall)

def sym_wand(a, x, y, s=1.0, c=GOLD):
    w = 1.3 * max(s, .8)
    a.line(x, y - 11 * s, x, y + 12 * s, stroke=c, w=w)
    for (yy, dx) in ((-4, 4), (2, -4), (7, 4)):
        a.line(x, y + yy * s, x + dx * s, y + (yy - 3) * s, stroke=c, w=w * .8)
    a.path(f"M{f(x)},{f(y - 11 * s)}Q{f(x + 3 * s)},{f(y - 14 * s)} {f(x)},{f(y - 18 * s)}Q{f(x - 3 * s)},{f(y - 14 * s)} {f(x)},{f(y - 11 * s)}Z",
           stroke=c, w=w * .8, fill=c, fa=.35)


def sym_cup(a, x, y, s=1.0, c=LAV):
    w = 1.3 * max(s, .8)
    a.path(f"M{f(x - 7 * s)},{f(y - 9 * s)}Q{f(x - 7 * s)},{f(y + 2 * s)} {f(x)},{f(y + 3 * s)}Q{f(x + 7 * s)},{f(y + 2 * s)} {f(x + 7 * s)},{f(y - 9 * s)}Z",
           stroke=c, w=w, fill=c, fa=.18)
    a.line(x, y + 3 * s, x, y + 9 * s, stroke=c, w=w)
    a.path(f"M{f(x - 5 * s)},{f(y + 11 * s)}Q{f(x)},{f(y + 7 * s)} {f(x + 5 * s)},{f(y + 11 * s)}Z", stroke=c, w=w * .9)


def sym_sword(a, x, y, s=1.0, c=SILVER):
    w = 1.2 * max(s, .8)
    a.poly([(x, y - 13 * s), (x + 2 * s, y - 4 * s), (x + 2 * s, y + 5 * s), (x - 2 * s, y + 5 * s), (x - 2 * s, y - 4 * s)], stroke=c, w=w, fill=c, fa=.2)
    a.line(x - 6 * s, y + 5.5 * s, x + 6 * s, y + 5.5 * s, stroke=c, w=w * 1.1)
    a.line(x, y + 6 * s, x, y + 10 * s, stroke=c, w=w)
    a.circle(x, y + 11.6 * s, 1.5 * s, stroke=c, w=w * .8)


def sym_pentacle(a, x, y, s=1.0, c=GOLD):
    w = 1.2 * max(s, .8)
    a.circle(x, y, 9 * s, stroke=c, w=w, fill=c, fa=.12)
    pts = [(x + 7 * s * math.cos(math.radians(-90 + i * 72)), y + 7 * s * math.sin(math.radians(-90 + i * 72))) for i in range(5)]
    order = [0, 2, 4, 1, 3]
    a.poly([pts[i] for i in order], stroke=c, w=w * .7)


SUIT_SYM = {"wands": sym_wand, "cups": sym_cup, "swords": sym_sword, "pentacles": sym_pentacle}
SUIT_COL = {"wands": GOLD, "cups": LAV, "swords": SILVER, "pentacles": GOLD}


# ---------------------------------------------------------------- frame and back

def frame(a, seed, accent=GOLD):
    a.path(f"M10,0H130A10,10 0 0,1 140,10V230A10,10 0 0,1 130,240H10A10,10 0 0,1 0,230V10A10,10 0 0,1 10,0Z", stroke=None, w=0, fill=FACE)
    a.path("M12,6H128A6,6 0 0,1 134,12V228A6,6 0 0,1 128,234H12A6,6 0 0,1 6,228V12A6,6 0 0,1 12,6Z", stroke=accent, w=.9)
    a.path("M13,9.5H127A3.5,3.5 0 0,1 130.5,13V227A3.5,3.5 0 0,1 127,230.5H13A3.5,3.5 0 0,1 9.5,227V13A3.5,3.5 0 0,1 13,9.5Z", stroke=LAV, w=.4, sa=.45)
    for y in (36, 200):  # separators for the numeral and name bands
        a.line(44, y, 64, y, stroke=accent, w=.6, sa=.8)
        a.line(76, y, 96, y, stroke=accent, w=.6, sa=.8)
        a.poly([(70, y - 2.6), (72.6, y), (70, y + 2.6), (67.4, y)], stroke=accent, w=.6)
    rnd = random.Random(seed)
    placed = 0
    while placed < 14:
        x, y = rnd.uniform(16, 124), rnd.uniform(42, 194)
        if math.hypot(x - CX, (y - CY) * .8) < 52:
            continue
        a.dot(x, y, rnd.uniform(.4, .9), MOON, rnd.uniform(.35, .8))
        placed += 1


def card_back():
    a = Art()
    a.path(f"M10,0H130A10,10 0 0,1 140,10V230A10,10 0 0,1 130,240H10A10,10 0 0,1 0,230V10A10,10 0 0,1 10,0Z", stroke=None, w=0, fill=BACK)
    a.path("M12,6H128A6,6 0 0,1 134,12V228A6,6 0 0,1 128,234H12A6,6 0 0,1 6,228V12A6,6 0 0,1 12,6Z", stroke=GOLD, w=1)
    a.path("M14,10H126A4,4 0 0,1 130,14V226A4,4 0 0,1 126,230H14A4,4 0 0,1 10,226V14A4,4 0 0,1 14,10Z", stroke=LAV, w=.4, sa=.5)
    rnd = random.Random(7)
    for _ in range(60):
        a.dot(rnd.uniform(16, 124), rnd.uniform(16, 224), rnd.uniform(.3, .8), MOON, rnd.uniform(.25, .7))
    # celestial circle with 12 ticks (zodiac ring), crescent orbiting a central star — the ASTRA mark
    a.circle(CX, 120, 40, stroke=GOLD, w=.8)
    a.circle(CX, 120, 34, stroke=GOLD, w=.4, sa=.6)
    for i in range(12):
        ang = math.radians(i * 30)
        a.line(CX + 34 * math.cos(ang), 120 + 34 * math.sin(ang), CX + 40 * math.cos(ang), 120 + 40 * math.sin(ang), stroke=GOLD, w=.5)
    a.star(CX, 120, 9, 3.2, n=4, rot=-90, stroke=GOLD, w=.8, fill=GOLD, fa=.9)
    a.star(CX, 120, 5, 2.2, n=4, rot=-45, stroke=GOLD, w=.5, fill=GOLD, fa=.5)
    a.arc(CX, 120, 22, 200, 340, stroke=LAV, w=.6, sa=.8)
    a.crescent(CX + 22 * math.cos(math.radians(330)), 120 + 22 * math.sin(math.radians(330)), 5, phase=.5, rot=150, stroke=None, w=0, fill=MOON)
    for y in (52, 188):
        a.line(40, y, 100, y, stroke=GOLD, w=.5, sa=.7)
        a.star(CX, y, 3.5, 1.2, n=4, stroke=GOLD, w=.4, fill=GOLD)
    for cx, cy in ((24, 24), (116, 24), (24, 216), (116, 216)):
        a.star(cx, cy, 4, 1.4, n=4, stroke=GOLD, w=.4, fill=GOLD, fa=.8)
    return a


# ---------------------------------------------------------------- Major Arcana

def m0(a):  # The Fool — the leap: a ledge, an arc of steps into open sky, a star
    a.path("M22,170H54L60,178V192", w=1.2)
    a.wave(18, 54, 176, amp=1, n=4, stroke=GOLD, w=.5, sa=.5)
    for i in range(9):
        t = i / 8
        x = 56 + (98 - 56) * t
        y = 166 - 94 * math.sin(t * math.pi / 2) - 8 * t
        a.dot(x, y, .8 + .5 * t, LAV, .9)
    a.star(100, 64, 13, 4.5, n=8, stroke=GOLD, w=.8, fill=GOLD, fa=.25)
    a.circle(38, 62, 7, stroke=GOLD, w=.8)
    a.rays(38, 62, 10, 13, 12, stroke=GOLD, w=.5)
    a.path("M80,150q6,-10 12,0q6,10 12,0", stroke=LAV, w=.7)  # little wind / path ahead


def m1(a):  # The Magician — as above, so below: infinity, the wand, the four suits
    a.lemniscate(70, 64, 18, stroke=GOLD, w=1)
    a.line(70, 82, 70, 146, w=1.4)
    a.circle(70, 80, 2.5, stroke=GOLD, w=.8, fill=GOLD, fa=.6)
    a.circle(70, 148, 2.5, stroke=GOLD, w=.8, fill=GOLD, fa=.6)
    a.line(28, 162, 112, 162, stroke=LAV, w=.8)
    sym_wand(a, 36, 150, .45); sym_cup(a, 56, 152, .45); sym_sword(a, 84, 152, .45); sym_pentacle(a, 104, 152, .45)
    a.rays(70, 64, 22, 26, 16, stroke=GOLD, w=.4, sa=.6)


def m2(a):  # The High Priestess — between two pillars, the veil and the triple moon
    for x in (34, 96):
        a.path(f"M{x},76H{x + 10}V172H{x}Z", stroke=GOLD, w=.9, fill=PURPLE, fa=.25)
        a.line(x - 2, 76, x + 12, 76, w=1.1); a.line(x - 2, 172, x + 12, 172, w=1.1)
    a.circle(70, 66, 6, stroke=MOON, w=.8)
    a.crescent(58, 66, 6, phase=.5, rot=180, stroke=MOON, w=.7)
    a.crescent(82, 66, 6, phase=.5, rot=0, stroke=MOON, w=.7)
    for y in (96, 112, 128, 144):
        a.path(f"M46,{y}q6,6 12,0q6,6 12,0q6,6 12,0q6,6 12,0", stroke=LAV, w=.6, sa=.8)
    for x, y in ((52, 104), (70, 120), (88, 104), (58, 136), (82, 136)):
        a.dot(x, y, 1.4, LAV, .8)
    a.crescent(70, 164, 8, phase=.35, rot=-90, stroke=MOON, w=.8, fill=MOON, fa=.2)


def m3(a):  # The Empress — a crown of twelve stars, the seed of growth, wheat
    for i in range(12):
        ang = math.radians(195 + i * (150 / 11))
        a.star(70 + 40 * math.cos(ang), 104 + 40 * math.sin(ang), 3.2, 1.3, n=5, stroke=GOLD, w=.5, fill=GOLD, fa=.8)
    a.circle(70, 110, 15, stroke=LAV, w=1.1, fill=LAV, fa=.12)
    a.line(70, 125, 70, 146, stroke=LAV, w=1.1); a.line(62, 136, 78, 136, stroke=LAV, w=1.1)
    for x, d in ((34, 1), (106, -1)):
        a.path(f"M{x},176Q{x + 4 * d},150 {x + 2 * d},120", w=.9)
        for k in range(5):
            y = 128 + k * 9
            a.path(f"M{x + 3 * d - .5 * k * d},{y}l{4 * d},-5", w=.8)
            a.path(f"M{x + 3 * d - .5 * k * d},{y}l{-4 * d},-5", w=.8)
    a.circle(70, 110, 5, stroke=GOLD, w=.7, fill=GOLD, fa=.5)


def m4(a):  # The Emperor — the square of order, ram's horns, a crown
    a.path("M46,98H94V146H46Z", w=1.2, fill=PURPLE, fa=.25)
    a.circle(70, 122, 14, stroke=GOLD, w=.8)
    a.dot(70, 122, 2.5, GOLD)
    for d in (1, -1):
        x = 70 + 30 * d
        a.path(f"M{70 + 14 * d},96C{70 + 22 * d},82 {x + 6 * d},84 {x},94C{x - 3 * d},100 {x - 10 * d},96 {x - 8 * d},90", w=1)
    a.poly([(56, 80), (60, 66), (66, 76), (70, 60), (74, 76), (80, 66), (84, 80)], w=1, fill=GOLD, fa=.2)
    a.line(54, 84, 86, 84, w=1)
    a.line(40, 160, 100, 160, stroke=LAV, w=.8); a.line(48, 168, 92, 168, stroke=LAV, w=.6, sa=.7)


def m5(a):  # The Hierophant — the triple staff and two crossed keys
    a.line(70, 62, 70, 150, w=1.3)
    for y, hw in ((72, 7), (82, 10), (92, 13)):
        a.line(70 - hw, y, 70 + hw, y, w=1.1)
    for d in (1, -1):
        ang = math.radians(90 + 35 * d)
        x0, y0 = 70 + 26 * math.cos(ang) * -1, 160 - 26 * math.sin(ang)
        x1, y1 = 70 - 26 * math.cos(ang) * -1, 160 + 26 * math.sin(ang) * .2
        col = GOLD if d == 1 else SILVER
        a.line(70 - 22 * d, 138, 70 + 18 * d, 174, stroke=col, w=1.1)
        a.circle(70 - 25 * d, 135, 5, stroke=col, w=1)
        a.path(f"M{f(70 + 18 * d)},{174}l{f(4 * d)},-4m{f(-6 * d)},2l{f(4 * d)},-4", stroke=col, w=1)
    a.arc(70, 92, 30, 200, 340, stroke=LAV, w=.6, sa=.8)


def m6(a):  # The Lovers — a radiant sun over two overlapping circles
    a.circle(70, 64, 9, stroke=GOLD, w=1, fill=GOLD, fa=.2)
    a.rays(70, 64, 12, 18, 16, stroke=GOLD, w=.6)
    a.circle(58, 126, 22, stroke=GOLD, w=1.1)
    a.circle(82, 126, 22, stroke=LAV, w=1.1)
    a.path("M70,108A22,22 0 0,1 70,144A22,22 0 0,1 70,108Z", stroke=None, w=0, fill=LAV, fa=.25)
    a.line(40, 168, 100, 168, stroke=LAV, w=.6, sa=.7)
    for x in (44, 96):
        a.path(f"M{x},168q-4,-12 0,-20q4,8 0,20", stroke=GOLD, w=.6)


def m7(a):  # The Chariot — a canopy of stars, the body, crescents, two wheels
    for i in range(5):
        a.star(46 + i * 12, 66 - 4 * math.sin(i * math.pi / 4), 3, 1.2, n=5, stroke=GOLD, w=.5, fill=GOLD, fa=.8)
    a.arc(70, 104, 30, 205, 335, stroke=LAV, w=.7)
    a.path("M44,100H96V140H44Z", w=1.1, fill=PURPLE, fa=.25)
    a.star(70, 120, 8, 3, n=8, stroke=GOLD, w=.7, fill=GOLD, fa=.3)
    a.crescent(44, 98, 5, phase=.45, rot=180, stroke=MOON, w=.7)
    a.crescent(96, 98, 5, phase=.45, rot=0, stroke=MOON, w=.7)
    for x in (50, 90):
        a.circle(x, 156, 13, stroke=GOLD, w=1)
        a.rays(x, 156, 2, 13, 6, rot=15, stroke=GOLD, w=.6)
    a.line(30, 170, 110, 170, stroke=LAV, w=.6, sa=.6)


def m8(a):  # Strength — infinity over the lion's mane: gentle, steady courage
    a.lemniscate(70, 66, 14, stroke=GOLD, w=1)
    a.circle(70, 122, 17, stroke=GOLD, w=1, fill=PURPLE, fa=.45)
    for i in range(16):
        ang = math.radians(i * 22.5)
        x0, y0 = 70 + 19 * math.cos(ang), 122 + 19 * math.sin(ang)
        x1, y1 = 70 + 29 * math.cos(ang), 122 + 29 * math.sin(ang)
        mx, my = 70 + 24 * math.cos(ang + .18), 122 + 24 * math.sin(ang + .18)
        a.path(f"M{f(x0)},{f(y0)}Q{f(mx)},{f(my)} {f(x1)},{f(y1)}", w=.8)
    for k in range(9):
        ang = math.radians(200 + k * 17.5)
        a.circle(70 + 40 * math.cos(ang), 122 + 40 * math.sin(ang), 2, stroke=LAV, w=.6)
    a.star(70, 122, 7, 2.5, n=4, stroke=GOLD, w=.7, fill=GOLD, fa=.5)


def m9(a):  # The Hermit — the lantern's star, a staff, the peaks
    a.path("M22,180L42,150L54,164L72,134L90,160L102,146L118,180", stroke=LAV, w=.9)
    a.line(46, 176, 58, 72, w=1.2)
    a.path("M58,76Q74,72 86,88", w=.7)
    a.poly([(80, 92), (92, 92), (86, 86)], w=.8)
    a.path("M79,92H93V118H79Z", w=1, fill=GOLD, fa=.1)
    a.path("M86,114Q80,108 84,101Q86,104 87,99Q93,106 86,114Z", w=.8, fill=GOLD, fa=.45)
    a.rays(86, 105, 16, 22, 12, stroke=GOLD, w=.5, sa=.7)
    a.dot(98, 60, 1.2, MOON); a.dot(34, 72, .9, MOON)


def m10(a):  # Wheel of Fortune — the turning wheel
    a.circle(70, 114, 42, w=1.1)
    a.circle(70, 114, 34, w=.7)
    a.circle(70, 114, 8, w=.9, fill=GOLD, fa=.3)
    a.rays(70, 114, 8, 34, 8, rot=22.5, stroke=GOLD, w=.8)
    for i in range(24):
        ang = math.radians(i * 15)
        a.line(70 + 36 * math.cos(ang), 114 + 36 * math.sin(ang), 70 + 40 * math.cos(ang), 114 + 40 * math.sin(ang), stroke=LAV, w=.5)
    a.arc(70, 114, 50, 110, 160, stroke=LAV, w=.8)
    a.arc(70, 114, 50, 290, 340, stroke=LAV, w=.8)
    for x, y in ((30, 62), (110, 62), (30, 168), (110, 168)):
        a.star(x, y, 4, 1.5, n=4, stroke=GOLD, w=.5, fill=GOLD, fa=.7)


def m11(a):  # Justice — the upright sword and the scales
    a.poly([(70, 58), (73, 70), (73, 142), (67, 142), (67, 70)], stroke=SILVER, w=1, fill=SILVER, fa=.15)
    a.line(58, 142, 82, 142, stroke=SILVER, w=1.3)
    a.line(70, 142, 70, 156, stroke=SILVER, w=1.2)
    a.circle(70, 160, 3, stroke=SILVER, w=.9)
    a.line(34, 92, 106, 92, w=1.1)
    for x in (40, 100):
        a.line(x, 92, x - 10, 118, w=.6); a.line(x, 92, x + 10, 118, w=.6)
        a.path(f"M{x - 12},118Q{x},130 {x + 12},118Z", w=.9, fill=GOLD, fa=.15)
    a.circle(70, 92, 2.5, w=.8, fill=GOLD, fa=.6)


def m12(a):  # The Hanged Man — a pause; seen upside down, a halo of new sight
    a.line(30, 66, 110, 66, w=1.3)
    a.line(36, 66, 36, 178, stroke=LAV, w=.8); a.line(104, 66, 104, 178, stroke=LAV, w=.8)
    a.line(70, 66, 70, 98, w=.9)
    a.path("M70,98L80,108L70,116", w=1)
    a.line(70, 98, 70, 124, w=1.1)
    a.poly([(56, 124), (84, 124), (70, 142)], w=1, fill=GOLD, fa=.15)
    a.circle(70, 152, 9, stroke=LAV, w=.9, fill=LAV, fa=.15)
    a.rays(70, 152, 12, 18, 12, stroke=GOLD, w=.6)


def m13(a):  # Death — the sun sets between two towers; a rose rises: endings that make room
    a.line(24, 150, 116, 150, w=1)
    a.path("M48,150A22,22 0 0,1 92,150", w=1.1, fill=LAV, fa=.3)
    for k in range(7):
        ang = math.radians(198 + k * 24)
        a.line(70 + 26 * math.cos(ang), 150 + 26 * math.sin(ang), 70 + 32 * math.cos(ang), 150 + 32 * math.sin(ang), w=.6)
    for x in (28, 100):
        a.path(f"M{x},150V114H{x + 12}V150", stroke=LAV, w=.9)
        a.path(f"M{x},114v-4h3v3h3v-3h3v3h3v-3", stroke=LAV, w=.7)
    a.wave(28, 112, 160, amp=1.6, n=8, stroke=LAV, w=.6, sa=.8)
    a.wave(38, 102, 168, amp=1.2, n=6, stroke=LAV, w=.5, sa=.6)
    for i in range(5):
        ang = math.radians(-90 + i * 72)
        a.circle(70 + 5 * math.cos(ang), 74 + 5 * math.sin(ang), 4, stroke=MOON, w=.7)
    a.dot(70, 74, 1.6, GOLD)
    a.path("M70,82Q68,94 70,104", stroke=LAV, w=.8)


def m14(a):  # Temperance — water flowing between two cups; a rising sun
    sym_cup(a, 46, 116, 1.1, GOLD)
    sym_cup(a, 94, 146, 1.1, LAV)
    for k in range(3):
        a.path(f"M{50 + k * 1.5},{106 + k}C{66},{96 + k * 2} {84},{118 + k * 2} {91 + k * 1.5},{137 - k}", stroke=MOON, w=.5, sa=.85)
    a.circle(98, 66, 7, stroke=GOLD, w=.9)
    a.rays(98, 66, 10, 14, 12, stroke=GOLD, w=.5)
    a.path("M26,180Q50,166 66,170T100,96", stroke=LAV, w=.5, sa=.6)
    for x in (32, 40):
        a.path(f"M{x},176q-3,-6 0,-10q3,4 0,10", stroke=LAV, w=.7)
    a.poly([(66, 62), (74, 62), (70, 55)], w=.7)


def m15(a):  # The Devil — what holds us: horns, and two linked rings that can be lifted off
    a.path("M54,96C46,86 42,72 46,58", w=1.2)
    a.path("M86,96C94,86 98,72 94,58", w=1.2)
    a.path("M54,96A16,16 0 0,0 86,96", stroke=LAV, w=.9)
    a.dot(64, 92, 1.1, LAV); a.dot(76, 92, 1.1, LAV)
    a.circle(61, 136, 12, w=1.1)
    a.circle(79, 136, 12, w=1.1)
    a.line(70, 112, 70, 126, stroke=SILVER, w=.8)
    a.path("M48,160H92V172H48Z", stroke=LAV, w=.9, fill=PURPLE, fa=.3)
    a.line(61, 148, 61, 160, stroke=SILVER, w=.7); a.line(79, 148, 79, 160, stroke=SILVER, w=.7)


def m16(a):  # The Tower — a sudden flash, the crown thrown off: structures shifting
    a.path("M54,172V96H86V172", w=1.1, fill=PURPLE, fa=.2)
    for y in (110, 132, 154):
        a.path(f"M66,{y}h8v8h-8z", stroke=GOLD, w=.6, fill=GOLD, fa=.3)
    a.line(46, 172, 94, 172, w=1)
    a.poly([(88, 88), (91, 76), (96, 84), (101, 72), (104, 84), (109, 78), (108, 92)], w=1, fill=GOLD, fa=.25)
    a.path("M38,48L56,70L48,72L66,94", stroke=MOON, w=1.4)
    for x, y in ((40, 104), (100, 118), (34, 130), (106, 146), (44, 156)):
        a.line(x, y, x + 2, y + 6, stroke=GOLD, w=.8)


def m17(a):  # The Star — hope: a great star, seven companions, water poured out
    a.star(70, 82, 22, 7, n=8, stroke=GOLD, w=1, fill=GOLD, fa=.25)
    a.star(70, 82, 12, 4, n=8, rot=-67.5, stroke=GOLD, w=.6)
    for i in range(7):
        ang = math.radians(200 + i * 23)
        a.star(70 + 42 * math.cos(ang), 96 + 42 * math.sin(ang), 4, 1.5, n=8, stroke=MOON, w=.5, fill=MOON, fa=.7)
    a.wave(26, 114, 164, amp=2, n=8, stroke=LAV, w=.8)
    a.wave(34, 106, 172, amp=1.4, n=6, stroke=LAV, w=.6, sa=.7)
    a.path("M52,128Q48,146 46,162", stroke=MOON, w=.7, sa=.9)
    a.path("M88,128Q92,146 96,162", stroke=MOON, w=.7, sa=.9)
    sym_cup(a, 52, 124, .5, LAV); sym_cup(a, 88, 124, .5, LAV)


def m18(a):  # The Moon — the uncertain path between two towers, under the moon
    a.crescent(70, 76, 18, phase=.4, rot=200, stroke=MOON, w=1, fill=MOON, fa=.15)
    a.circle(70, 76, 24, stroke=LAV, w=.5, sa=.7)
    for x, y in ((56, 108), (70, 112), (84, 108)):
        a.path(f"M{x},{y}q2,4 0,6q-2,-2 0,-6", stroke=GOLD, w=.7, fill=GOLD, fa=.4)
    for x in (26, 102):
        a.path(f"M{x},166V130H{x + 12}V166", stroke=LAV, w=.9)
        a.path(f"M{x + 4},138h4v6h-4z", stroke=GOLD, w=.5)
    a.path("M70,182C56,170 84,160 70,148S78,128 70,122", w=.9)
    a.wave(24, 116, 176, amp=1.6, n=8, stroke=LAV, w=.6)


def m19(a):  # The Sun — joy and clarity: a full sun, sunflowers on the wall
    a.circle(70, 90, 20, w=1.2, fill=GOLD, fa=.95)
    a.circle(70, 90, 14, stroke=FACE, w=.6, sa=.5)
    for i in range(16):
        ang = math.radians(i * 22.5)
        if i % 2 == 0:
            a.line(70 + 24 * math.cos(ang), 90 + 24 * math.sin(ang), 70 + 38 * math.cos(ang), 90 + 38 * math.sin(ang), w=1)
        else:
            x0, y0 = 70 + 24 * math.cos(ang), 90 + 24 * math.sin(ang)
            x1, y1 = 70 + 34 * math.cos(ang), 90 + 34 * math.sin(ang)
            mx, my = 70 + 29 * math.cos(ang + .12), 90 + 29 * math.sin(ang + .12)
            a.path(f"M{f(x0)},{f(y0)}Q{f(mx)},{f(my)} {f(x1)},{f(y1)}", w=.8)
    a.line(26, 152, 114, 152, stroke=LAV, w=1)
    for x in range(30, 112, 12):
        a.line(x, 152, x, 160, stroke=LAV, w=.5)
    a.line(26, 160, 114, 160, stroke=LAV, w=.6)
    for x in (40, 60, 80, 100):
        a.circle(x, 142, 3, w=.7, fill=GOLD, fa=.5)
        a.rays(x, 142, 4, 7, 8, stroke=GOLD, w=.5)
        a.line(x, 149, x, 152, w=.5)


def m20(a):  # Judgement — the call: a trumpet's rays, rising from the water
    a.path("M38,62L84,92", w=1.4)
    a.path("M84,92L96,84L102,104L88,98Z", w=1, fill=GOLD, fa=.25)
    a.path("M58,74V96H72V82", stroke=LAV, w=.7)
    a.line(65, 82, 65, 92, stroke=LAV, w=.6); a.line(60, 87, 70, 87, stroke=LAV, w=.6)
    for i in range(7):
        ang = math.radians(-10 + i * 18)
        a.line(100 + 8 * math.cos(ang), 96 + 8 * math.sin(ang), 100 + 18 * math.cos(ang), 96 + 18 * math.sin(ang), w=.5)
    a.wave(24, 116, 170, amp=1.8, n=8, stroke=LAV, w=.8)
    for x in (46, 70, 94):
        a.path(f"M{x},166V146", w=1)
        a.path(f"M{x - 7},134L{x},146L{x + 7},134", w=1)
        a.circle(x, 140, 2.2, stroke=GOLD, w=.7)


def m21(a):  # The World — completion: the wreath, a star within, four corners held
    rx, ry = 30, 46
    for i in range(36):
        t = math.radians(i * 10)
        x, y = 70 + rx * math.cos(t), 114 + ry * math.sin(t)
        tx, ty = -rx * math.sin(t), ry * math.cos(t)
        n = math.hypot(tx, ty); tx, ty = tx / n, ty / n
        a.path(f"M{f(x - tx * 3)},{f(y - ty * 3)}Q{f(x + ty * 2.5)},{f(y - tx * 2.5)} {f(x + tx * 3)},{f(y + ty * 3)}", w=.8)
    for y, d in ((68, 1), (160, -1)):
        a.path(f"M64,{y - 4 * d}L76,{y + 4 * d}M76,{y - 4 * d}L64,{y + 4 * d}", stroke=LAV, w=1)
    a.star(70, 114, 12, 4.4, n=8, stroke=GOLD, w=.8, fill=GOLD, fa=.3)
    a.circle(70, 114, 18, stroke=LAV, w=.5, sa=.7)
    for x, y in ((28, 60), (112, 60), (28, 168), (112, 168)):
        a.circle(x, y, 5, stroke=GOLD, w=.7)
        a.dot(x, y, 1.6, GOLD)


MAJORS = [m0, m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11, m12, m13, m14, m15, m16, m17, m18, m19, m20, m21]

# ---------------------------------------------------------------- Minor Arcana

PIPS = {
    2: [(70, 84), (70, 144)],
    3: [(70, 72), (70, 114), (70, 156)],
    4: [(48, 82), (92, 82), (48, 146), (92, 146)],
    5: [(48, 80), (92, 80), (70, 114), (48, 148), (92, 148)],
    6: [(48, 74), (92, 74), (48, 114), (92, 114), (48, 154), (92, 154)],
    7: [(48, 74), (92, 74), (70, 94), (48, 114), (92, 114), (48, 154), (92, 154)],
    8: [(48, 72), (92, 72), (70, 92), (48, 114), (92, 114), (70, 136), (48, 156), (92, 156)],
    9: [(48, 66), (92, 66), (48, 96), (92, 96), (70, 114), (48, 132), (92, 132), (48, 162), (92, 162)],
    10: [(48, 66), (92, 66), (70, 80), (48, 96), (92, 96), (48, 132), (92, 132), (70, 148), (48, 162), (92, 162)],
}
PIP_SCALE = {2: 1.35, 3: 1.2, 4: 1.25, 5: 1.15, 6: 1.05, 7: 1.0, 8: .95, 9: .9, 10: .88}


def minor(a, suit, n):
    sym, col = SUIT_SYM[suit], SUIT_COL[suit]
    if n == 1:  # Ace: the suit's gift, radiant
        a.circle(70, 114, 34, stroke=col, w=.6, sa=.7)
        a.rays(70, 114, 37, 44, 24, stroke=GOLD, w=.6, sa=.8)
        a.circle(70, 114, 28, stroke=None, w=0, fill=PURPLE, fa=.25)
        sym(a, 70, 116, 2.1, col)
        for y in (64, 164):
            a.star(70, y, 4, 1.5, n=4, stroke=GOLD, w=.5, fill=GOLD, fa=.8)
    elif n <= 10:
        s = PIP_SCALE[n]
        for (x, y) in PIPS[n]:
            sym(a, x, y + (2 if suit == "pentacles" else 0), s, col)
    else:  # courts: an arch, the suit's emblem, and the rank's sign above
        a.path("M36,176V96A34,34 0 0,1 104,96V176", stroke=col, w=.7, sa=.6)
        a.path("M40,176V98A30,30 0 0,1 100,98V176", stroke=col, w=.35, sa=.4)
        sym(a, 70, 130, 2.0, col)
        y = 82
        if n == 11:  # Page: a small four-point star, beginnings
            a.star(70, y, 9, 3, n=4, stroke=GOLD, w=.8, fill=GOLD, fa=.3)
        elif n == 12:  # Knight: forward motion, a double chevron
            a.path(f"M58,{y + 6}L70,{y - 4}L82,{y + 6}", w=1.1)
            a.path(f"M58,{y + 13}L70,{y + 3}L82,{y + 13}", w=1.1, sa=.7)
        elif n == 13:  # Queen: a crescent crown
            a.crescent(70, y + 2, 8, phase=.45, rot=-90, stroke=GOLD, w=.9, fill=GOLD, fa=.25)
            for x in (60, 70, 80):
                a.dot(x, y - 8 if x == 70 else y - 5, 1.3, GOLD)
        else:  # King: a five-point crown
            a.poly([(56, y + 8), (58, y - 4), (63, y + 2), (70, y - 8), (77, y + 2), (82, y - 4), (84, y + 8)], w=1, fill=GOLD, fa=.25)
            a.line(55, y + 11, 85, y + 11, w=1)


# ---------------------------------------------------------------- output

def to_svg(a, size=1):
    body = []
    for d, stroke, w, fill, sa, fa in a.items:
        attrs = [f'd="{d}"']
        attrs.append(f'fill="{fill}" fill-opacity="{fa}"' if fill else 'fill="none"')
        if stroke:
            attrs.append(f'stroke="{stroke}" stroke-width="{w}" stroke-opacity="{sa}" stroke-linecap="round" stroke-linejoin="round"')
        body.append(f"<path {' '.join(attrs)}/>")
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W * size}" height="{H * size}">' + "".join(body) + "</svg>"


def to_vector(a):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- ASTRA deck astra-deck-1 · original design · generated by tools/tarot/make_cards.py, do not edit -->',
           f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{W}dp" android:height="{H}dp" '
           f'android:viewportWidth="{W}" android:viewportHeight="{H}">']
    for d, stroke, w, fill, sa, fa in a.items:
        attrs = [f'android:pathData="{d}"']
        if fill:
            attrs.append(f'android:fillColor="{fill}"')
            if fa != 1: attrs.append(f'android:fillAlpha="{fa:.2f}"')
        if stroke:
            attrs += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{w}"',
                      'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
            if sa != 1: attrs.append(f'android:strokeAlpha="{sa:.2f}"')
        out.append("  <path " + " ".join(attrs) + "/>")
    out.append("</vector>")
    return "\n".join(out) + "\n"


def cards():
    """(id, Art) for all 78 cards in deck order (matches Tarot.kt ids)."""
    out = []
    for i, fn in enumerate(MAJORS):
        a = Art(); frame(a, i); fn(a); out.append((f"maj{i:02d}", a))
    for suit in ("wands", "cups", "swords", "pentacles"):
        for n in range(1, 15):
            a = Art(); frame(a, len(suit) * 31 + n); minor(a, suit, n); out.append((f"{suit}{n:02d}", a))
    return out


def main():
    os.makedirs(PREV, exist_ok=True)
    deck = cards()
    assert len(deck) == 78
    back = card_back()
    for cid, a in deck + [("back", back)]:
        with open(os.path.join(RES, f"tarot_{cid}.xml"), "w") as fh:
            fh.write(to_vector(a))
        with open(os.path.join(PREV, f"{cid}.svg"), "w") as fh:
            fh.write(to_svg(a))
    kt = os.path.join(ROOT, "app/src/main/java/app/astra/ui/tarot/TarotArt.kt")
    os.makedirs(os.path.dirname(kt), exist_ok=True)
    with open(kt, "w") as fh:
        fh.write("// Generated by tools/tarot/make_cards.py. Do not edit.\npackage app.astra.ui.tarot\n\nimport app.astra.R\n\n")
        fh.write("/** Drawable for each card in astra-deck-1 (original designs). */\nobject TarotArt {\n    val BACK = R.drawable.tarot_back\n\n")
        fh.write("    fun of(cardId: String): Int = when (cardId) {\n")
        for cid, _ in deck:
            fh.write(f'        "{cid}" -> R.drawable.tarot_{cid}\n')
        fh.write("        else -> R.drawable.tarot_back\n    }\n}\n")
    only = sys.argv[1:] or [c for c, _ in deck] + ["back"]
    tiles = "".join(f'<div class="t"><img src="{c}.svg"><span>{c}</span></div>' for c in only)
    with open(os.path.join(PREV, "sheet.html"), "w") as fh:
        fh.write(f"""<!doctype html><meta charset=utf-8><style>
body{{background:#05070f;margin:0;padding:12px;display:flex;flex-wrap:wrap;gap:10px;font:11px sans-serif;color:#aab}}
.t{{display:flex;flex-direction:column;align-items:center;gap:3px}} img{{width:{int(W * 1.5)}px;height:{int(H * 1.5)}px}}</style>{tiles}""")
    print(f"wrote {len(deck) + 1} cards")


if __name__ == "__main__":
    main()
