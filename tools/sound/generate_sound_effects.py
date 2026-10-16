"""Synthesise GeoQuiz's three original "game-feel" sound effects (task 8.5).

No audio downloaded or copied from anywhere: every sample is a plain sine tone generated with
the standard library (`math`, `wave`), with a short linear fade in/out on each note so splicing
notes together (and the clip's start/end) never clicks. Output is 16-bit PCM mono WAV, which
Android's SoundPool plays directly from `res/raw` with no extra decoding step.

- `sound_correct.wav`: a quick rising two-note chime (a bright, friendly "ding-ding").
- `sound_wrong.wav`: a short, low buzz (unmistakably different from "correct", not punishing).
- `sound_perfect.wav`: a brighter three-note ascending arpeggio (a little flourish).

Usage:
    python tools/sound/generate_sound_effects.py

Standard library only; deterministic (same bytes every run).
"""

import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 22_050
AMPLITUDE = 0.5  # of full scale; leaves headroom so nothing clips
FADE_SECONDS = 0.012  # linear fade in/out per note, long enough to avoid an audible click

OUTPUT_DIR = (
    Path(__file__).resolve().parent.parent.parent
    / "app"
    / "src"
    / "main"
    / "res"
    / "raw"
)


def _note(frequency_hz: float, duration_seconds: float) -> list[float]:
    """One sine-wave note at [-1, 1], with a short linear fade in/out."""
    sample_count = int(SAMPLE_RATE * duration_seconds)
    fade_samples = max(1, int(SAMPLE_RATE * FADE_SECONDS))
    samples = []
    for i in range(sample_count):
        t = i / SAMPLE_RATE
        value = math.sin(2 * math.pi * frequency_hz * t)
        envelope = 1.0
        if i < fade_samples:
            envelope = i / fade_samples
        elif i > sample_count - fade_samples:
            envelope = (sample_count - i) / fade_samples
        samples.append(value * envelope)
    return samples


def _silence(duration_seconds: float) -> list[float]:
    return [0.0] * int(SAMPLE_RATE * duration_seconds)


def _write_wav(path: Path, samples: list[float]) -> None:
    frames = b"".join(
        struct.pack("<h", int(max(-1.0, min(1.0, s)) * AMPLITUDE * 32767)) for s in samples
    )
    with wave.open(str(path), "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(SAMPLE_RATE)
        wav_file.writeframes(frames)


def build_correct() -> list[float]:
    """A quick rising two-note chime: a bright, friendly confirm."""
    return _note(880.0, 0.09) + _silence(0.02) + _note(1_320.0, 0.14)


def build_wrong() -> list[float]:
    """A short, low buzz: clearly not "correct", not harsh or punishing."""
    return _note(180.0, 0.22)


def build_perfect() -> list[float]:
    """A brighter three-note ascending major arpeggio (a little flourish): C6, E6, G6."""
    return (
        _note(1_046.5, 0.10)
        + _silence(0.015)
        + _note(1_318.5, 0.10)
        + _silence(0.015)
        + _note(1_568.0, 0.22)
    )


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    clips = {
        "sound_correct.wav": build_correct(),
        "sound_wrong.wav": build_wrong(),
        "sound_perfect.wav": build_perfect(),
    }
    for name, samples in clips.items():
        path = OUTPUT_DIR / name
        _write_wav(path, samples)
        print(f"wrote {path} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
