#!/usr/bin/env python3
"""Turn the ElevenLabs takes listed in tools/audio/hellsounds.py into the mod's sounds.

    python3 tools/audio/build_sounds.py

Reads tools/audio/cache/<event>/<n>.flac (sound effects) and tools/audio/cache/music/<name>.mp3 (music). The cache is not
in git: the .ogg files it makes are. tools/audio/manifest.txt has the ElevenLabs generation ids of every take, so a lost
cache can be fetched again from the ElevenLabs flow without paying twice. Writes:
  assets/hellcraft/sounds/sfx/<event with dots as folders>_<n>.ogg   mono, Vorbis q3, silence trimmed, peak -1 dB
      (loops, `loop` in the spec: crossfaded end into start so they repeat without a seam, quieter)
  assets/hellcraft/sounds/music/<name>.ogg                          stereo, Vorbis q0 (about 64 kbit/s), faded in and out
      (the guardians' fights have two each, music/<guardian>_1.ogg and _2.ogg, played by music/BossTheme by phase;
      Lucifer's tracks are music/duel.ogg, enraged.ogg and true_form.ogg as `music/BossMusic` expects, under the events
      lucifer.<track>; the rest are music.<name>)
  their entries in assets/hellcraft/sounds.json (gen_sounds.py's "cue.*" entries are left alone; every other entry is
      rewritten from the spec), subtitles in lang/en_us.json, and
  net.thesmallthings.hellcraft.sound.HellSounds (a Holder<SoundEvent> per event, plus a few helpers).
Deterministic: re-running leaves git clean.
"""
import json
import os
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from hellsounds import SFX, MUSIC  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
V = os.path.join(ROOT, "versions", "26.3", "src", "main")
ASSETS = os.path.join(V, "resources", "assets", "hellcraft")
SOUNDS = os.path.join(ASSETS, "sounds")
CACHE = os.path.join(HERE, "cache")
JAVA = os.path.join(V, "java", "net", "thesmallthings", "hellcraft", "sound", "HellSounds.java")
RATE = 44100
CROSSFADE = 2.0  # seconds a loop's end is laid over its start
# sounds.json volume for a few events heard often, and Soundscape's beds (0.5) and details (0.6), which it plays at 1.0
# (the rest are set where they are played)
VOLUME = {"entity.damned.scream": 0.6, "entity.damned.sigh": 0.4}
LUCIFER = {"lucifer.duel": "duel", "lucifer.enraged": "enraged", "lucifer.true_form": "true_form"}


def decode(path, channels):
    out = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-f", "s16le", "-ac", str(channels), "-ar", str(RATE), "-"],
                         check=True, capture_output=True).stdout
    a = np.frombuffer(out, np.int16).astype(np.float64) / 32768.0
    return a.reshape(-1, channels)


def encode(signal, path, quality):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    channels = signal.shape[1]
    pcm = (np.clip(signal, -1, 1) * 32767).round().astype("<i2").tobytes()
    with tempfile.NamedTemporaryFile(suffix=".raw", delete=False) as raw:
        raw.write(pcm)
        name = raw.name
    try:
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(RATE), "-ac", str(channels), "-i", name,
                        "-c:a", "libvorbis", "-q:a", str(quality), "-map_metadata", "-1", "-fflags", "+bitexact",
                        "-flags:a", "+bitexact", path], check=True)
    finally:
        os.remove(name)


def ramp(n, rising=True):
    t = np.linspace(0.0, 1.0, n)[:, None]
    return np.sin(t * np.pi / 2) if rising else np.cos(t * np.pi / 2)


def trim(a, threshold_db=-48.0):
    """Cut the silence before and after a sound, keeping a few ms of lead and a short fade out."""
    level = np.abs(a).max(axis=1)
    loud = np.nonzero(level > 10 ** (threshold_db / 20))[0]
    if len(loud) == 0:
        return a
    start = max(0, loud[0] - int(0.005 * RATE))
    end = min(len(a), loud[-1] + int(0.08 * RATE))
    a = a[start:end].copy()
    fade = min(len(a) // 4, int(0.04 * RATE))
    if fade > 1:
        a[-fade:] *= ramp(fade, False)
    lead = min(len(a) // 4, int(0.003 * RATE))
    if lead > 1:
        a[:lead] *= ramp(lead)
    return a


def seamless(a):
    """A loop: the last CROSSFADE seconds are laid over the first (equal power), so the end flows into the start."""
    x = min(int(CROSSFADE * RATE), len(a) // 3)
    out = a[:len(a) - x].copy()
    out[:x] = a[:x] * ramp(x) + a[len(a) - x:] * ramp(x, False)
    return out


def peak(a, db):
    p = np.abs(a).max()
    return a if p == 0 else a * (10 ** (db / 20) / p)


def rms(a, db, ceiling_db=-1.0):
    r = np.sqrt(np.mean(a ** 2))
    if r == 0:
        return a
    a = a * (10 ** (db / 20) / r)
    p = np.abs(a).max()
    ceiling = 10 ** (ceiling_db / 20)
    return a * (ceiling / p) if p > ceiling else a


def const(name):
    return name.replace(".", "_").upper()


def main():
    with open(os.path.join(ASSETS, "sounds.json")) as f:
        old = json.load(f)
    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(lang_path) as f:
        lang = json.load(f)
    sounds = {k: v for k, v in old.items() if k.startswith("cue.")}
    missing = []

    for name, spec in SFX.items():
        prompt, seconds, takes, subtitle = spec[:4]
        loop = len(spec) > 4 and spec[4]
        entries = []
        for n in range(1, takes + 1):
            src = os.path.join(CACHE, name, f"{n}.flac")
            if not os.path.exists(src):
                missing.append(src)
                continue
            a = decode(src, 1)
            a = rms(seamless(a), -22.0) if loop else peak(trim(a), -1.0)
            rel = "sfx/" + name.replace(".", "/") + f"_{n}"
            encode(a, os.path.join(SOUNDS, rel + ".ogg"), 3)
            volume = VOLUME.get(name, 0.5 if name.endswith(".bed") else 0.6 if name.endswith(".detail") else None)
            entries.append({"name": "hellcraft:" + rel, **({"volume": volume} if volume else {})})
        entry = {}
        key = "subtitles.hellcraft." + name
        if subtitle:
            entry["subtitle"] = key
            lang[key] = subtitle
        elif "subtitle" in old.get(name, {}):
            entry["subtitle"] = old[name]["subtitle"]  # an ambient detail keeps its subtitle (Soundscape's)
        entry["sounds"] = entries
        sounds[name] = entry

    for name, (prompt, seconds) in MUSIC.items():
        src = os.path.join(CACHE, "music", name + ".mp3")
        if not os.path.exists(src):
            missing.append(src)
            continue
        a = trim(decode(src, 2), -60.0)
        fin, fout = int(0.5 * RATE), int(4.0 * RATE)
        a[:fin] *= ramp(fin)
        a[-fout:] *= ramp(fout, False)
        a = rms(a, -18.0)
        file = LUCIFER.get(name, name)
        encode(a, os.path.join(SOUNDS, "music", file + ".ogg"), 0)
        event = name if name in LUCIFER else "music." + name
        sounds[event] = {"sounds": [{"name": "hellcraft:music/" + file, "stream": True}]}

    if missing:
        sys.exit("Missing takes (fetch them first):\n  " + "\n  ".join(missing))

    with open(os.path.join(ASSETS, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2, ensure_ascii=False)
        f.write("\n")
    with open(lang_path, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")

    lines = []
    group = None
    for name, spec in SFX.items():
        g = name.split(".")[0]
        if g != group:
            lines.append("")
            lines.append(f"\t// {g}")
            group = g
        doc = spec[3] or ("Loop: " if len(spec) > 4 and spec[4] else "") + spec[0]
        lines.append(f"\t/** {doc.replace('*/', '')}. */")
        lines.append(f"\tpublic static final Holder<SoundEvent> {const(name)} = event(\"{name}\");")
    lines.append("")
    lines.append("\t// music (Lucifer's tracks are played by music/BossMusic; the guardians' <id>_1 and _2 by music/BossTheme)")
    lines.append("\tprivate static final Map<String, Holder<SoundEvent>> MUSIC = new HashMap<>();")
    for name in MUSIC:
        if name in LUCIFER:
            continue
        lines.append(f"\tpublic static final Holder<SoundEvent> MUSIC_{const(name)} = music(\"{name}\", event(\"music.{name}\"));")
    lines.append("")
    lines.append("\tprivate static Holder<SoundEvent> music(String name, Holder<SoundEvent> sound) {")
    lines.append("\t\tMUSIC.put(name, sound);")
    lines.append("\t\treturn sound;")
    lines.append("\t}")
    lines.append("")
    lines.append("\t/** A music track by its name in tools/audio/hellsounds.py (\"limbo\", \"minos_2\"), or null. */")
    lines.append("\t@Nullable")
    lines.append("\tpublic static Holder<SoundEvent> music(String name) {")
    lines.append("\t\treturn MUSIC.get(name);")
    lines.append("\t}")
    body = "\n".join(lines)
    java = f"""package net.thesmallthings.hellcraft.sound;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.thesmallthings.hellcraft.HellcraftMod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

// Generated by tools/audio/build_sounds.py from tools/audio/hellsounds.py: edit those, not this file.

/**
 * Hellcraft's own sounds (made with ElevenLabs; the prompts are in tools/audio/hellsounds.py). Every player runs the
 * mod, so these play from its jar. They are registered (the biomes' music and ambience name them by id), so
 * {{@link #register}} runs while the mod starts, before the registries freeze.
 */
public final class HellSounds {{
	private HellSounds() {{
	}}

	private static Holder<SoundEvent> event(String name) {{
		Identifier id = HellcraftMod.id(name);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}}

	/** Registers every sound (the static fields do it; this only loads the class in time). */
	public static void register() {{
	}}
{body}
}}
"""
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w") as f:
        f.write(java)
    print(f"{len(SFX)} sound events, {len(MUSIC)} tracks")


if __name__ == "__main__":
    main()
