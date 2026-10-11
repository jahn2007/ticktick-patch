"""Read selected APK/Dex methods using only Python's standard library.

This is a focused bytecode inspector, not a Java decompiler. Offsets are DEX
16-bit code units; raw words are included so conclusions can be checked.
Format references: source.android.com/docs/core/runtime/{dex-format,dalvik-bytecode}
"""
import argparse
import hashlib
import json
import re
import struct
import zipfile
import zlib
from pathlib import Path


def signed(value, bits):
    return value - (1 << bits) if value & (1 << (bits - 1)) else value


def class_name(descriptor):
    return descriptor[1:-1].replace('/', '.') if descriptor.startswith('L') else descriptor


NAMES = {}
for start, names in (
    (0x00, 'nop move move/from16 move/16 move-wide move-wide/from16 move-wide/16 move-object move-object/from16 move-object/16 move-result move-result-wide move-result-object move-exception return-void return return-wide return-object const/4 const/16 const const/high16 const-wide/16 const-wide/32 const-wide const-wide/high16 const-string const-string/jumbo const-class monitor-enter monitor-exit check-cast instance-of array-length new-instance new-array filled-new-array filled-new-array/range fill-array-data throw goto goto/16 goto/32 packed-switch sparse-switch cmpl-float cmpg-float cmpl-double cmpg-double cmp-long if-eq if-ne if-lt if-ge if-gt if-le if-eqz if-nez if-ltz if-gez if-gtz if-lez'),
    (0x44, 'aget aget-wide aget-object aget-boolean aget-byte aget-char aget-short aput aput-wide aput-object aput-boolean aput-byte aput-char aput-short iget iget-wide iget-object iget-boolean iget-byte iget-char iget-short iput iput-wide iput-object iput-boolean iput-byte iput-char iput-short sget sget-wide sget-object sget-boolean sget-byte sget-char sget-short sput sput-wide sput-object sput-boolean sput-byte sput-char sput-short invoke-virtual invoke-super invoke-direct invoke-static invoke-interface'),
    (0x74, 'invoke-virtual/range invoke-super/range invoke-direct/range invoke-static/range invoke-interface/range'),
    (0x7b, 'neg-int not-int neg-long not-long neg-float neg-double int-to-long int-to-float int-to-double long-to-int long-to-float long-to-double float-to-int float-to-long float-to-double double-to-int double-to-long double-to-float int-to-byte int-to-char int-to-short'),
    (0x90, 'add-int sub-int mul-int div-int rem-int and-int or-int xor-int shl-int shr-int ushr-int add-long sub-long mul-long div-long rem-long and-long or-long xor-long shl-long shr-long ushr-long add-float sub-float mul-float div-float rem-float add-double sub-double mul-double div-double rem-double'),
    (0xb0, 'add-int/2addr sub-int/2addr mul-int/2addr div-int/2addr rem-int/2addr and-int/2addr or-int/2addr xor-int/2addr shl-int/2addr shr-int/2addr ushr-int/2addr add-long/2addr sub-long/2addr mul-long/2addr div-long/2addr rem-long/2addr and-long/2addr or-long/2addr xor-long/2addr shl-long/2addr shr-long/2addr ushr-long/2addr add-float/2addr sub-float/2addr mul-float/2addr div-float/2addr rem-float/2addr add-double/2addr sub-double/2addr mul-double/2addr div-double/2addr rem-double/2addr'),
    (0xd0, 'add-int/lit16 rsub-int mul-int/lit16 div-int/lit16 rem-int/lit16 and-int/lit16 or-int/lit16 xor-int/lit16 add-int/lit8 rsub-int/lit8 mul-int/lit8 div-int/lit8 rem-int/lit8 and-int/lit8 or-int/lit8 xor-int/lit8 shl-int/lit8 shr-int/lit8 ushr-int/lit8'),
    (0xfa, 'invoke-polymorphic invoke-polymorphic/range invoke-custom invoke-custom/range const-method-handle const-method-type'),
):
    NAMES.update((start + i, name) for i, name in enumerate(names.split()))


class Dex:
    def __init__(self, data, name):
        self.data, self.name = data, name
        assert data[:4] == b'dex\n' and data[4:7] in (b'035', b'037', b'038', b'039', b'040')
        assert self.u32(32) == len(data) and self.u32(40) == 0x12345678
        assert self.u32(8) == zlib.adler32(data[12:]) & 0xffffffff
        assert data[12:32] == hashlib.sha1(data[32:]).digest()
        string_count, string_offset = self.pair(56)
        self.strings = []
        for i in range(string_count):
            _, offset = self.uleb(self.u32(string_offset + 4 * i))
            end = data.index(b'\0', offset)
            self.strings.append(data[offset:end].replace(b'\xc0\x80', b'\0').decode('utf-8', 'replace'))
        count, offset = self.pair(64)
        self.types = [self.strings[self.u32(offset + 4 * i)] for i in range(count)]
        count, offset = self.pair(72)
        self.protos = []
        for i in range(count):
            _, result, params = struct.unpack_from('<III', data, offset + 12 * i)
            args = [self.types[self.u16(params + 4 + 2 * n)] for n in range(self.u32(params))] if params else []
            self.protos.append('(' + ''.join(args) + ')' + self.types[result])
        count, offset = self.pair(80)
        self.fields = []
        for i in range(count):
            owner, kind, field = struct.unpack_from('<HHI', data, offset + 8 * i)
            self.fields.append(class_name(self.types[owner]) + '.' + self.strings[field] + ':' + self.types[kind])
        count, offset = self.pair(88)
        self.methods = []
        for i in range(count):
            owner, proto, method = struct.unpack_from('<HHI', data, offset + 8 * i)
            self.methods.append((class_name(self.types[owner]), self.strings[method], self.protos[proto]))
        count, offset = self.pair(96)
        self.classes = []
        for i in range(count):
            owner, flags, parent, interfaces, source, annotations, class_data, static = struct.unpack_from('<8I', data, offset + 32 * i)
            methods = []
            if class_data:
                sizes = []
                pos = class_data
                for _ in range(4):
                    size, pos = self.uleb(pos)
                    sizes.append(size)
                for _ in range(sizes[0] + sizes[1]):
                    _, pos = self.uleb(pos)
                    _, pos = self.uleb(pos)
                for size in sizes[2:]:
                    index = 0
                    for _ in range(size):
                        diff, pos = self.uleb(pos)
                        access, pos = self.uleb(pos)
                        code, pos = self.uleb(pos)
                        index += diff
                        methods.append((index, access, code))
            self.classes.append({'name': class_name(self.types[owner]),
                                 'source': None if source == 0xffffffff else self.strings[source],
                                 'methods': methods})

    def u16(self, offset): return struct.unpack_from('<H', self.data, offset)[0]
    def u32(self, offset): return struct.unpack_from('<I', self.data, offset)[0]
    def pair(self, offset): return struct.unpack_from('<II', self.data, offset)

    def uleb(self, offset):
        value = shift = 0
        for _ in range(5):
            byte = self.data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if byte < 128: return value, offset
            shift += 7
        raise ValueError('Oversized uleb128')

    def method_name(self, index):
        owner, name, proto = self.methods[index]
        return owner + '.' + name + proto

    def instructions(self, offset):
        if not offset: return []
        count = self.u32(offset + 12)
        words = struct.unpack_from('<' + str(count) + 'H', self.data, offset + 16)
        pc = 0
        result = []
        while pc < count:
            word, op = words[pc], words[pc] & 255
            arg = word >> 8
            size = 1
            text = ''
            if op == 0 and arg:
                if arg == 1: size = 4 + 2 * words[pc + 1]
                elif arg == 2: size = 2 + 4 * words[pc + 1]
                elif arg == 3:
                    length = words[pc + 2] | words[pc + 3] << 16
                    size = 4 + (words[pc + 1] * length + 1) // 2
                else: raise ValueError('Unknown payload')
            elif op in (3, 6, 9, 0x14, 0x17, 0x1b, 0x24, 0x25, 0x26, 0x2a, 0x2b, 0x2c, 0xfc, 0xfd) or 0x6e <= op <= 0x72 or 0x74 <= op <= 0x78: size = 3
            elif op == 0x18: size = 5
            elif op in (0xfa, 0xfb): size = 4
            elif op in (2, 5, 8, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23, 0x29, 0xfe, 0xff) or 0x2d <= op <= 0x3d or 0x44 <= op <= 0x6d or 0x90 <= op <= 0xaf or 0xd0 <= op <= 0xe2: size = 2
            assert pc + size <= count, (self.name, offset, pc, size)
            chunk = list(words[pc:pc + size])
            name = NAMES.get(op, 'unknown-' + hex(op))
            if op == 0 and arg: name = 'payload-' + str(arg)
            elif op in (0x0a, 0x0b, 0x0c, 0x0d, 0x0f, 0x10, 0x11, 0x1d, 0x1e, 0x27): text = 'v' + str(arg)
            elif op == 0x12: text = 'v%d, %d' % (arg & 15, signed(arg >> 4, 4))
            elif op in (0x13, 0x16): text = 'v%d, %d' % (arg, signed(chunk[1], 16))
            elif op in (0x14, 0x17): text = 'v%d, %d' % (arg, signed(chunk[1] | chunk[2] << 16, 32))
            elif op == 0x18: text = 'v%d, %d' % (arg, signed(sum(chunk[i] << (16 * (i - 1)) for i in range(1, 5)), 64))
            elif op in (0x15, 0x19): text = 'v%d, %d' % (arg, signed(chunk[1], 16) << (16 if op == 0x15 else 48))
            elif op in (0x1a, 0x1b):
                index = chunk[1] | (chunk[2] << 16 if op == 0x1b else 0)
                text = 'v%d, %s' % (arg, repr(self.strings[index]))
            elif 0x52 <= op <= 0x5f: text = 'v%d, v%d, %s' % (arg & 15, arg >> 4, self.fields[chunk[1]])
            elif 0x60 <= op <= 0x6d: text = 'v%d, %s' % (arg, self.fields[chunk[1]])
            elif 0x6e <= op <= 0x72 or op == 0xfa:
                regs = [(chunk[2] >> (4 * n)) & 15 for n in range(4)] + [arg & 15]
                text = '{' + ', '.join('v' + str(r) for r in regs[:arg >> 4]) + '}, ' + self.method_name(chunk[1])
            elif 0x74 <= op <= 0x78 or op == 0xfb:
                text = '{' + ', '.join('v' + str(r) for r in range(chunk[2], chunk[2] + arg)) + '}, ' + self.method_name(chunk[1])
            elif 0x32 <= op <= 0x37: text = 'v%d, v%d, -> %04x' % (arg & 15, arg >> 4, pc + signed(chunk[1], 16))
            elif 0x38 <= op <= 0x3d: text = 'v%d, -> %04x' % (arg, pc + signed(chunk[1], 16))
            elif op == 0x28: text = '-> %04x' % (pc + signed(arg, 8))
            elif op == 0x29: text = '-> %04x' % (pc + signed(chunk[1], 16))
            elif op in (0x2a, 0x2b, 0x2c, 0x26): text = 'v%d, -> %04x' % (arg, pc + signed(chunk[1] | chunk[2] << 16, 32))
            elif op in (0x1c, 0x1f, 0x22): text = 'v%d, %s' % (arg, self.types[chunk[1]])
            elif op in (1, 4, 7, 0x21) or 0x7b <= op <= 0x8f or 0xb0 <= op <= 0xcf: text = 'v%d, v%d' % (arg & 15, arg >> 4)
            elif op in (2, 5, 8): text = 'v%d, v%d' % (arg, chunk[1])
            elif op in (3, 6, 9): text = 'v%d, v%d' % (chunk[1], chunk[2])
            elif 0x2d <= op <= 0x31 or 0x44 <= op <= 0x51 or 0x90 <= op <= 0xaf: text = 'v%d, v%d, v%d' % (arg, chunk[1] & 255, chunk[1] >> 8)
            result.append({'pc': pc, 'op': name, 'args': text, 'raw': chunk})
            pc += size
        return result

    def selected(self, classes, sources, methods):
        result = []
        for cls in self.classes:
            if not (classes and classes.search(cls['name']) or sources and sources.search(cls['source'] or '')): continue
            selected = []
            for index, flags, code in cls['methods']:
                if not methods.search(self.methods[index][1]): continue
                selected.append({'method': self.method_name(index), 'flags': flags,
                                 'code_offset': code, 'registers': self.u16(code) if code else None,
                                 'inputs': self.u16(code + 2) if code else None,
                                 'instructions': self.instructions(code)})
            result.append({'dex': self.name, 'class': cls['name'], 'source': cls['source'], 'methods': selected})
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--class-regex')
    parser.add_argument('--source-regex')
    parser.add_argument('--method-regex', default='.*')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if not args.class_regex and not args.source_regex: parser.error('Select classes or source names')
    result = []
    with zipfile.ZipFile(args.apk) as apk:
        for name in apk.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                dex = Dex(apk.read(name), name)
                result.extend(dex.selected(re.compile(args.class_regex) if args.class_regex else None,
                                           re.compile(args.source_regex) if args.source_regex else None,
                                           re.compile(args.method_regex)))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=True, indent=2), encoding='utf-8')
    for cls in result:
        print(cls['dex'], cls['class'], 'source=' + str(cls['source']))
        for method in cls['methods']:
            print(' ', method['method'], 'registers', method['registers'], 'inputs', method['inputs'])
            for insn in method['instructions']:
                print('   %04x: %-24s %-100s [%s]' % (insn['pc'], insn['op'], insn['args'], ' '.join('%04x' % w for w in insn['raw'])))


if __name__ == '__main__': main()
