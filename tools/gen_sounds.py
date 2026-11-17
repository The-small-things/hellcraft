#!/usr/bin/env python3
"""Hellcraft's own sounds, synthesised in code (like tools/gen_textures.py draws the textures): the cues that let a
fight be read by ear. Each is built from sine partials, filtered noise and envelopes with numpy, written as 16-bit
mono and encoded to Ogg Vorbis with ffmpeg (needs ffmpeg with libvorbis).

Writes, for every cue:
  versions/26.3/src/main/resources/assets/hellcraft/sounds/cue/<name>.ogg
  its entry in assets/hellcraft/sounds.json ("cue.<name>"; the file's other entries are left alone)
  its subtitle in assets/hellcraft/lang/en_us.json
and client/sound/gen/CueTimes.java: how long before its moment each cue must start (a danger cue builds up and lands
exactly when the blow does).

Any cue can be replaced by a real recording later: drop an .ogg of the same name in and remove it from CUES here.

Run from the repo root:  python3 tools/gen_sounds.py
"""
import json
import os
import subprocess
import tempfile

import numpy as np

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "versions", "26.3")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hellcraft")
SOUNDS = os.path.join(ASSETS, "sounds", "cue")
CLIENT = os.path.join(ROOT, "src", "client", "java", "net", "thesmallthings", "hellcraft", "client", "sound", "gen")
RATE = 44100
rng = np.random.default_rng(666)


# ----------------------------------------------------------------------------------------------- building blocks

def t_axis(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def sweep(f0, f1, seconds, curve=1.0):
    """The phase of a tone gliding from f0 to f1 Hz (curve > 1 lingers low, then rushes up)."""
    t = t_axis(seconds)
    k = (t / seconds) ** curve
    f = f0 + (f1 - f0) * k
    return 2 * np.pi * np.cumsum(f) / RATE


def tone(freq, seconds, partials=((1, 1.0),), detune=0.0):
    t = t_axis(seconds)
    out = np.zeros_like(t)
    for ratio, amp in partials:
        out += amp * np.sin(2 * np.pi * freq * ratio * t * (1 + detune * rng.uniform(-1, 1)))
    return out


def noise(seconds):
    return rng.uniform(-1, 1, int(seconds * RATE))


def lowpass(x, cutoff):
    """One-pole low-pass (run twice for a gentler 12 dB slope)."""
    a = np.exp(-2 * np.pi * cutoff / RATE)
    y = np.zeros_like(x)
    acc = 0.0
    for _ in range(2):
        for i in range(len(x)):
            acc = (1 - a) * x[i] + a * acc
            y[i] = acc
        x = y.copy()
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def band(x, lo, hi):
    return lowpass(highpass(x, lo), hi)


def env(n, attack=0.01, release=0.2, shape=2.0):
    """An attack-release envelope over n samples (seconds for the edges)."""
    e = np.ones(n)
    a = min(n, max(1, int(attack * RATE)))
    r = min(n - a, max(1, int(release * RATE)))
    e[:a] = np.linspace(0, 1, a) ** 0.5
    e[n - r:] = np.linspace(1, 0, r) ** shape
    return e


def decay(n, seconds):
    return np.exp(-np.arange(n) / RATE / max(1e-4, seconds))


def swell(n, power=2.0):
    return np.linspace(0, 1, n) ** power


def at(length, *parts):
    """Lays (start seconds, signal) parts on one track `length` seconds long."""
    out = np.zeros(int(length * RATE))
    for start, sig in parts:
        i = int(start * RATE)
        j = min(len(out), i + len(sig))
        if j > i:
            out[i:j] += sig[:j - i]
    return out


def bell(freq, seconds, ratios=(1, 2.76, 5.40, 8.93), hardness=1.0):
    """A struck metal: inharmonic partials, the higher ones dying sooner."""
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    out = np.zeros(n)
    for k, r in enumerate(ratios):
        out += (0.6 ** k) * np.sin(2 * np.pi * freq * r * t) * np.exp(-t * (2.5 + 3.5 * k * hardness) / seconds)
    click = noise(0.004) * 0.6
    out[:len(click)] += click
    return out


def normalise(x, peak=0.89):
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


# ----------------------------------------------------------------------------------------------- the cues
# each: name -> (builder, lead seconds (how long before its moment it starts; 0 = plays on the moment), subtitle)

def red():
    """Red, get out: a low horn swelling for the warning, cut off by a dull thump as the blow lands."""
    lead = 0.9
    n = int(lead * RATE)
    t = t_axis(lead)
    horn = np.zeros(n)
    for f, a in ((73.4, 1.0), (110.0, 0.7)):
        phase = 2 * np.pi * f * t + 0.4 * np.sin(2 * np.pi * 5.5 * t)
        # a brassy tone: harmonics falling off like a sawtooth's, none above about 1.2 kHz
        for h in range(1, int(1200 / f) + 1):
            horn += a * np.sin(h * phase) / h ** 1.2
    horn = lowpass(horn, 700) * swell(n, 1.6)
    thump = tone(55, 0.35, ((1, 1.0), (2, 0.3))) * decay(int(0.35 * RATE), 0.08)
    thump += lowpass(noise(0.35), 300) * decay(int(0.35 * RATE), 0.05) * 0.8
    return at(lead + 0.4, (0, horn * 0.7), (lead, thump * 1.4)), lead


def white():
    """White, block now: a bright rising shimmer, and a hard 'ting' on the very moment to have the guard up."""
    lead = 0.6
    n = int(lead * RATE)
    rise = np.sin(sweep(700, 2100, lead, 2.2)) + 0.4 * np.sin(sweep(1400, 4200, lead, 2.2))
    rise += band(noise(lead), 3000, 9000) * 0.25
    rise *= swell(n, 2.4)
    ting = bell(2350, 0.55, hardness=0.7)
    return at(lead + 0.55, (0, rise * 0.45), (lead - 0.005, ting * 1.0)), lead


def purple():
    """Purple, can't be blocked (jump or roll): a sour whine rising in a tritone, wobbling, and a rush as it lands."""
    lead = 0.8
    n = int(lead * RATE)
    pa = sweep(260, 620, lead, 1.4)
    pb = sweep(260 * 1.414, 620 * 1.414, lead, 1.4)
    t = t_axis(lead)
    whine = np.zeros(n)
    for h, amp in ((1, 1.0), (2, 0.5), (3, 0.3), (4, 0.15)):
        whine += amp * (np.sin(h * pa) + 0.8 * np.sin(h * pb))
    whine *= (0.65 + 0.35 * np.sin(2 * np.pi * 9 * t)) * swell(n, 1.8)
    rush = band(noise(0.3), 400, 3000) * decay(int(0.3 * RATE), 0.06)
    return at(lead + 0.3, (0, whine), (lead, rush * 0.5)), lead


def parry():
    """A parry: a clean, high ring of steel on steel, with a bright crack on top."""
    ring = bell(1480, 0.9, ratios=(1, 2.32, 4.25, 6.63), hardness=0.5)
    crack = band(noise(0.03), 2500, 12000) * decay(int(0.03 * RATE), 0.008)
    return at(0.9, (0, ring), (0, crack * 1.2)), 0.0


def guard_break():
    """A guard beaten down: a heavy, dull clang that buzzes, and a crunch."""
    clang = bell(330, 0.8, ratios=(1, 1.47, 2.09, 2.56, 3.92), hardness=1.2)
    tb = t_axis(0.5)
    buzz = sum(np.sin(2 * np.pi * 98 * h * tb) / h for h in (1, 3, 5, 7, 9)) * decay(int(0.5 * RATE), 0.12) * 0.3
    crunch = lowpass(noise(0.2), 1800) * decay(int(0.2 * RATE), 0.05)
    return at(0.8, (0, clang), (0, buzz), (0.01, crunch)), 0.0


def dodge():
    """A blow slipped through a roll: a quick airy whoosh, rising."""
    n = int(0.35 * RATE)
    w = band(noise(0.35), 600, 5000) * np.sin(np.linspace(0, np.pi, n)) ** 2
    w += 0.15 * np.sin(sweep(400, 1200, 0.35)) * np.sin(np.linspace(0, np.pi, n))
    return w, 0.0


def posture_crack():
    """A boss's posture strained near breaking: a glassy tick and a short creak."""
    tick = bell(3100, 0.25, ratios=(1, 1.9, 3.1), hardness=1.5)
    creak = np.sin(sweep(180, 120, 0.2)) * decay(int(0.2 * RATE), 0.06) * 0.4
    return at(0.3, (0, tick), (0.02, creak)), 0.0


def posture_break():
    """A boss's posture broken: a shatter, a deep boom under it, and a long ring that says 'now'."""
    shatter = band(noise(0.6), 1500, 11000) * decay(int(0.6 * RATE), 0.12)
    boom = tone(48, 0.9, ((1, 1.0), (1.5, 0.4))) * decay(int(0.9 * RATE), 0.25)
    ring = bell(880, 1.4, ratios=(1, 2.0, 3.01, 4.2), hardness=0.4) * 0.5
    return at(1.4, (0, shatter), (0, boom * 1.3), (0.05, ring)), 0.0


# Lucifer's tells: one per attack, so each can be told apart without looking

def flurry():
    """The Seraph's flurry: three quick rising chimes, short, short, long: one for each cut."""
    out = at(0.9, (0, bell(1760, 0.25)), (0.18, bell(1980, 0.25)), (0.42, bell(2350, 0.45)))
    return out, 0.0


def feint():
    """The feint: the blade hums, held, wavering (the blow is late: wait for the ring)."""
    n = int(0.8 * RATE)
    t = t_axis(0.8)
    hum = (np.sin(2 * np.pi * 523 * t) + 0.5 * np.sin(2 * np.pi * 1046 * t)) * (0.6 + 0.4 * np.sin(2 * np.pi * 14 * t))
    return hum * env(n, 0.08, 0.1) * 0.5, 0.0


def dive():
    """The dive: a high whistle falling as he comes down on you."""
    n = int(0.9 * RATE)
    w = np.sin(sweep(2600, 500, 0.9, 0.7)) * swell(n, 1.2) * env(n, 0.01, 0.05)
    w += band(noise(0.9), 1000, 6000) * swell(n, 2.0) * 0.3
    return w * 0.6, 0.0


def crescent():
    """The crescent: a shimmering swish of light along the ice."""
    n = int(0.7 * RATE)
    s = band(noise(0.7), 2000, 10000) * np.sin(np.linspace(0, np.pi, n)) ** 1.5
    s += 0.3 * np.sin(sweep(1200, 3000, 0.7)) * np.sin(np.linspace(0, np.pi, n))
    return s * 0.8, 0.0


def counter():
    """The counter stance: a low, beating hum (don't strike while it sounds)."""
    n = int(1.6 * RATE)
    t = t_axis(1.6)
    hum = np.sin(2 * np.pi * 110 * t) + np.sin(2 * np.pi * 113.5 * t) + 0.4 * np.sin(2 * np.pi * 220 * t)
    return hum * env(n, 0.25, 0.3) * 0.45, 0.0


def blink():
    """The blink: a pop as he vanishes (he'll be behind you)."""
    pop = np.sin(sweep(900, 120, 0.12)) * decay(int(0.12 * RATE), 0.04)
    air = band(noise(0.25), 1500, 8000) * decay(int(0.25 * RATE), 0.06) * 0.6
    return at(0.3, (0, pop), (0.005, air)), 0.0


def hand_rise():
    """The Emperor raises a hand: a deep rumble swelling."""
    n = int(1.0 * RATE)
    r = lowpass(noise(1.0), 140) * 3.0 + tone(41, 1.0) * 0.5
    return r * swell(n, 1.5) * env(n, 0.01, 0.12), 0.0


def swat():
    """The swat: a huge low whoosh of a hand sweeping the ice."""
    n = int(0.6 * RATE)
    w = band(noise(0.6), 120, 1400) * np.sin(np.linspace(0, np.pi, n)) ** 2
    w += 0.4 * np.sin(sweep(90, 60, 0.6)) * np.sin(np.linspace(0, np.pi, n))
    return w * 1.2, 0.0


def clap():
    """The clap: both hands slammed together, thunder."""
    crack = lowpass(noise(0.08), 4000) * decay(int(0.08 * RATE), 0.015)
    roll = lowpass(noise(1.4), 220) * decay(int(1.4 * RATE), 0.4) * 2.2
    return at(1.4, (0, crack * 1.4), (0.01, roll)), 0.0


def slump():
    """The Emperor's posture broken: a vast, falling groan."""
    n = int(2.0 * RATE)
    t = t_axis(2.0)
    g = np.sin(sweep(70, 38, 2.0)) + 0.5 * np.sin(sweep(105, 57, 2.0)) + 0.3 * np.sign(np.sin(sweep(70, 38, 2.0)))
    g = lowpass(g * (0.7 + 0.3 * np.sin(2 * np.pi * 3 * t)), 500)
    return g * env(n, 0.15, 0.8) * 0.9, 0.0


CUES = {
    "red": (red, "A red mark: get out"),
    "white": (white, "A white mark: block now"),
    "purple": (purple, "A purple mark: jump or roll"),
    "parry": (parry, "Parried"),
    "guard_break": (guard_break, "Guard broken"),
    "dodge": (dodge, "A blow slips past"),
    "posture_crack": (posture_crack, "Posture strained"),
    "posture_break": (posture_break, "Posture broken"),
    "flurry": (flurry, "The Seraph's blade flickers"),
    "feint": (feint, "The Seraph's blade hums"),
    "dive": (dive, "The Seraph dives"),
    "crescent": (crescent, "A crescent of light"),
    "counter": (counter, "The Seraph waits, wings folded"),
    "blink": (blink, "The Seraph vanishes"),
    "hand_rise": (hand_rise, "The Emperor raises a hand"),
    "swat": (swat, "The Emperor swats"),
    "clap": (clap, "The Emperor's hands clap"),
    "slump": (slump, "The Emperor slumps"),
}


def encode(signal, path):
    pcm = (normalise(signal) * 32767).astype("<i2").tobytes()
    with tempfile.NamedTemporaryFile(suffix=".raw", delete=False) as raw:
        raw.write(pcm)
        name = raw.name
    try:
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(RATE), "-ac", "1", "-i", name,
                        "-c:a", "libvorbis", "-q:a", "5", "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", path],
                       check=True)
    finally:
        os.remove(name)


def main():
    os.makedirs(SOUNDS, exist_ok=True)
    os.makedirs(CLIENT, exist_ok=True)
    sounds_json = os.path.join(ASSETS, "sounds.json")
    with open(sounds_json) as f:
        sounds = json.load(f)
    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(lang_path) as f:
        lang = json.load(f)
    leads = {}
    for name, (build, subtitle) in CUES.items():
        signal, lead = build()
        signal = signal * env(len(signal), 0.002, 0.02, 1.0)
        encode(signal, os.path.join(SOUNDS, name + ".ogg"))
        sounds["cue." + name] = {"subtitle": "subtitles.hellcraft.cue." + name, "sounds": [{"name": "hellcraft:cue/" + name}]}
        lang["subtitles.hellcraft.cue." + name] = subtitle
        leads[name] = round(lead * 20)
    with open(sounds_json, "w") as f:
        json.dump(sounds, f, indent=2, ensure_ascii=False)
        f.write("\n")
    with open(lang_path, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    consts = "\n".join(f"\t/** {CUES[n][1]}. */\n\tpublic static final int {n.upper()} = {v};" for n, v in leads.items())
    java = f"""package net.thesmallthings.hellcraft.client.sound.gen;

// Generated by tools/gen_sounds.py: edit the script, not this file.

/** How many ticks before its moment each of Hellcraft's cue sounds must start (a danger cue lands as the blow does). */
public final class CueTimes {{
	private CueTimes() {{
	}}

{consts}
}}
"""
    with open(os.path.join(CLIENT, "CueTimes.java"), "w") as f:
        f.write(java)
    print(f"Wrote {len(CUES)} cue sounds to {os.path.relpath(SOUNDS)}")


if __name__ == "__main__":
    main()
