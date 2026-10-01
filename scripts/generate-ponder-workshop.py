#!/usr/bin/env python3
"""Write the native tutorial schematic; explicit air establishes Ponder's bounds."""
import gzip
import struct
from pathlib import Path


def utf(value):
    encoded = value.encode()
    return struct.pack('>H', len(encoded)) + encoded


def tag(kind, name, value):
    return bytes([kind]) + utf(name) + value


def integer(value):
    return struct.pack('>i', value)


def ints(values):
    return b'\x03' + integer(len(values)) + b''.join(map(integer, values))


def compound(values):
    return b''.join(values) + b'\x00'


def compounds(values):
    return b'\x0a' + integer(len(values)) + b''.join(values)


palette = [compound([tag(8, 'Name', utf(name))]) for name in ['minecraft:gray_concrete', 'minecraft:air']]
def write_workshop(name, width):
    blocks = [compound([tag(9, 'pos', ints([x, y, z])), tag(3, 'state', integer(0 if y == 0 else 1))])
              for x in range(width) for y in range(5) for z in range(width)]
    root = compound([tag(3, 'DataVersion', integer(3955)), tag(9, 'size', ints([width, 5, width])),
                     tag(9, 'palette', compounds(palette)), tag(9, 'blocks', compounds(blocks)),
                     tag(9, 'entities', compounds([]))])
    path = Path(__file__).resolve().parent.parent / f'src/main/resources/assets/magnetization/ponder/{name}.nbt'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(tag(10, '', root), mtime=0))
    print(path)


write_workshop('empty_workshop', 7)
write_workshop('thermal_workshop', 9)
