"""Writer and reader for the GeoQuiz map layer format (GQGM version 1).

The byte-level contract is documented in data/geo/FORMAT.md; keep the two in
step. The reader is used by the pipeline's self-check and by
test_geodata.py, so every file is decoded and validated after it is written.

Standard library only.
"""

from __future__ import annotations

import struct
from dataclasses import dataclass, field

MAGIC = b"GQGM"
FORMAT_VERSION = 1

# Layer ids (byte 6 of the header).
LAYER_ADMIN0_110M = 1
LAYER_ADMIN0_50M = 2
LAYER_TAP_ZONES = 3
LAYER_ADMIN1_50M = 4
LAYER_RIVERS_50M = 5
LAYER_LAKES_50M = 6
LAYER_REGIONS_50M = 7
LAYER_GEO_POINTS_50M = 8

# Geometry kinds (byte 7 of the header).
KIND_POINT = 1
KIND_LINE = 2
KIND_POLYGON = 3

# Property types.
PROP_STRING = 1
PROP_INT32 = 2
PROP_UINT8 = 3

# Part flags.
FLAG_HOLE = 0x01
FLAG_REMOTE = 0x02
FLAG_WRAP = 0x04

INT32_MISSING = -(2 ** 31)

Q_MIN = -32768
Q_MAX = 32767
Q_STEPS = 65535


@dataclass
class Part:
    flags: int
    points: list  # list of (int, int) quantised


@dataclass
class Feature:
    id: str
    props: list  # values in schema order
    bbox: tuple  # (minx, miny, maxx, maxy) quantised
    label: tuple  # (x, y) quantised
    parts: list = field(default_factory=list)


@dataclass
class Layer:
    layer_id: int
    kind: int
    bounds: tuple  # (minLon, minLat, maxLon, maxLat) float64
    source: str
    schema: list  # [(name, type)]
    features: list = field(default_factory=list)


class Quantiser:
    """Maps lon/lat to int16 against fixed layer bounds, and back."""

    def __init__(self, bounds):
        self.min_lon, self.min_lat, self.max_lon, self.max_lat = bounds
        self.sx = self.max_lon - self.min_lon
        self.sy = self.max_lat - self.min_lat
        if self.sx <= 0 or self.sy <= 0:
            raise ValueError(f"degenerate bounds {bounds}")

    def q(self, p) -> tuple[int, int]:
        x = int((p[0] - self.min_lon) / self.sx * Q_STEPS + 0.5) + Q_MIN
        y = int((p[1] - self.min_lat) / self.sy * Q_STEPS + 0.5) + Q_MIN
        return (min(Q_MAX, max(Q_MIN, x)), min(Q_MAX, max(Q_MIN, y)))

    def lon(self, x: int) -> float:
        return self.min_lon + (x - Q_MIN) * self.sx / Q_STEPS

    def lat(self, y: int) -> float:
        return self.min_lat + (y - Q_MIN) * self.sy / Q_STEPS

    def deg(self, p) -> tuple[float, float]:
        return (self.lon(p[0]), self.lat(p[1]))


# --------------------------------------------------------------------------
# Encoding
# --------------------------------------------------------------------------

def _varint(n: int, out: bytearray) -> None:
    if n < 0:
        raise ValueError("varint must be non-negative")
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return


def _zigzag(n: int) -> int:
    return 2 * n if n >= 0 else -2 * n - 1


def _string(s: str, out: bytearray) -> None:
    b = s.encode("utf-8")
    if len(b) > 0xFFFF:
        raise ValueError("string too long")
    out += struct.pack("<H", len(b))
    out += b


def encode(layer: Layer) -> bytes:
    out = bytearray()
    out += MAGIC
    out += struct.pack("<HBB", FORMAT_VERSION, layer.layer_id, layer.kind)
    out += struct.pack("<dddd", *layer.bounds)
    out += struct.pack("<I", len(layer.features))
    _string(layer.source, out)
    out.append(len(layer.schema))
    for name, typ in layer.schema:
        _string(name, out)
        out.append(typ)
    for f in layer.features:
        out += encode_feature(layer.schema, f)
    return bytes(out)


def encode_feature(schema: list, f: Feature) -> bytes:
    """Encode one feature record (used by encode and by the size breakdown)."""
    out = bytearray()
    _string(f.id, out)
    for (name, typ), v in zip(schema, f.props):
        if typ == PROP_STRING:
            _string(v, out)
        elif typ == PROP_INT32:
            out += struct.pack("<i", v)
        elif typ == PROP_UINT8:
            out.append(v)
        else:
            raise ValueError(typ)
    out += struct.pack("<hhhh", *f.bbox)
    out += struct.pack("<hh", *f.label)
    _varint(len(f.parts), out)
    cx, cy = f.bbox[0], f.bbox[1]
    for part in f.parts:
        out.append(part.flags)
        _varint(len(part.points), out)
        for x, y in part.points:
            _varint(_zigzag(x - cx), out)
            _varint(_zigzag(y - cy), out)
            cx, cy = x, y
    return bytes(out)


# --------------------------------------------------------------------------
# Decoding
# --------------------------------------------------------------------------

class _Buf:
    def __init__(self, data: bytes):
        self.d = data
        self.i = 0

    def take(self, n: int) -> bytes:
        if self.i + n > len(self.d):
            raise ValueError("unexpected end of file")
        b = self.d[self.i: self.i + n]
        self.i += n
        return b

    def unpack(self, fmt: str):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))

    def u8(self) -> int:
        return self.take(1)[0]

    def string(self) -> str:
        (n,) = self.unpack("<H")
        return self.take(n).decode("utf-8")

    def varint(self) -> int:
        shift = 0
        result = 0
        while True:
            b = self.u8()
            result |= (b & 0x7F) << shift
            if not b & 0x80:
                return result
            shift += 7
            if shift > 35:
                raise ValueError("varint too long")


def _unzigzag(n: int) -> int:
    return (n >> 1) ^ -(n & 1)


def decode(data: bytes) -> Layer:
    b = _Buf(data)
    if b.take(4) != MAGIC:
        raise ValueError("bad magic")
    version, layer_id, kind = b.unpack("<HBB")
    if version != FORMAT_VERSION:
        raise ValueError(f"unsupported version {version}")
    bounds = b.unpack("<dddd")
    (count,) = b.unpack("<I")
    source = b.string()
    nprops = b.u8()
    schema = []
    for _ in range(nprops):
        name = b.string()
        schema.append((name, b.u8()))
    layer = Layer(layer_id, kind, tuple(bounds), source, schema)
    for _ in range(count):
        fid = b.string()
        props = []
        for _, typ in schema:
            if typ == PROP_STRING:
                props.append(b.string())
            elif typ == PROP_INT32:
                props.append(b.unpack("<i")[0])
            elif typ == PROP_UINT8:
                props.append(b.u8())
            else:
                raise ValueError(f"unknown property type {typ}")
        fb = b.unpack("<hhhh")
        label = b.unpack("<hh")
        nparts = b.varint()
        cx, cy = fb[0], fb[1]
        parts = []
        for _ in range(nparts):
            flags = b.u8()
            npts = b.varint()
            pts = []
            for _ in range(npts):
                cx += _unzigzag(b.varint())
                cy += _unzigzag(b.varint())
                pts.append((cx, cy))
            parts.append(Part(flags, pts))
        layer.features.append(Feature(fid, props, tuple(fb), tuple(label), parts))
    if b.i != len(data):
        raise ValueError(f"{len(data) - b.i} trailing bytes")
    return layer
