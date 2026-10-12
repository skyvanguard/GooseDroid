"""Sintetiza los efectos de sonido de Goozy.

Todos los sonidos de la app salen de este script: nada se graba ni se toma de
otro lado. Escribe WAV mono de 16 bits en app/src/main/assets/sound/.

Uso:  python tools/gen_sounds.py
"""
import wave
from pathlib import Path

import numpy as np

RATE = 22050
OUT_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "sound"
RNG = np.random.default_rng(7)


def timeline(seconds: float) -> np.ndarray:
    return np.arange(int(RATE * seconds)) / RATE


def envelope(n: int, attack: float, release: float) -> np.ndarray:
    env = np.ones(n)
    a = max(1, int(RATE * attack))
    r = max(1, int(RATE * release))
    env[:a] = np.linspace(0, 1, a)
    env[-r:] *= np.linspace(1, 0, r)
    return env


def resonate(signal: np.ndarray, center_hz: float, bandwidth_hz: float) -> np.ndarray:
    """Filtro resonante de dos polos: da el timbre nasal del graznido."""
    r = np.exp(-np.pi * bandwidth_hz / RATE)
    theta = 2 * np.pi * center_hz / RATE
    a1, a2 = -2 * r * np.cos(theta), r * r
    out = np.zeros_like(signal)
    y1 = y2 = 0.0
    for i, x in enumerate(signal):
        y = x - a1 * y1 - a2 * y2
        out[i] = y
        y2, y1 = y1, y
    return out


def honk() -> np.ndarray:
    t = timeline(0.32)
    # Tono que sube y cae, con un temblor leve
    pitch = 330 + 90 * np.sin(np.pi * np.clip(t / 0.28, 0, 1)) + 6 * np.sin(2 * np.pi * 28 * t)
    phase = 2 * np.pi * np.cumsum(pitch) / RATE
    buzz = sum(np.sin(k * phase) / k for k in range(1, 16))
    buzz += 0.15 * RNG.standard_normal(len(t))
    voiced = 0.6 * resonate(buzz, 1050, 180) + 0.4 * resonate(buzz, 2600, 300)
    return voiced * envelope(len(t), 0.012, 0.09)


def pat() -> np.ndarray:
    t = timeline(0.12)
    thump = np.sin(2 * np.pi * 140 * t * (1 - 0.5 * t / 0.12))
    noise = resonate(RNG.standard_normal(len(t)), 900, 600) * 0.3
    return (thump + noise) * np.exp(-t * 38)


def mud() -> np.ndarray:
    t = timeline(0.38)
    noise = RNG.standard_normal(len(t))
    sweep = 400 + 900 * (t / t[-1])
    out = np.zeros_like(noise)
    chunk = 256
    for start in range(0, len(t), chunk):
        part = slice(start, start + chunk)
        out[part] = resonate(noise[part], float(sweep[start]), 220)
    wobble = 0.6 + 0.4 * np.sin(2 * np.pi * 11 * t)
    return out * wobble * envelope(len(t), 0.02, 0.15)


def chime() -> np.ndarray:
    out = []
    for freq, length in ((880, 0.11), (1318.5, 0.28)):
        t = timeline(length)
        tone = np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * 2 * freq * t)
        out.append(tone * np.exp(-t * 9) * envelope(len(t), 0.004, 0.03))
    return np.concatenate(out)


def write(name: str, samples: np.ndarray) -> None:
    peak = np.max(np.abs(samples)) or 1.0
    pcm = (samples / peak * 0.85 * 32767).astype("<i2")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT_DIR / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    print(f"{name}.wav  {len(pcm) / RATE:.2f} s")


if __name__ == "__main__":
    for sound_name, make in (("honk", honk), ("pat", pat), ("mud", mud), ("chime", chime)):
        write(sound_name, make())
