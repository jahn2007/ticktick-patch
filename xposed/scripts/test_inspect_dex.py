import struct
import unittest

from inspect_dex import Dex


def code(words, fields=(), methods=()):
    dex = Dex.__new__(Dex)
    dex.name = 'fixture.dex'
    dex.fields = list(fields)
    dex.methods = list(methods)
    dex.data = b'\0' * 4 + struct.pack('<HHHHII', 3, 1, 0, 0, 0, len(words))
    dex.data += struct.pack('<' + str(len(words)) + 'H', *words)
    return dex.instructions(4)


class InspectorTest(unittest.TestCase):
    def test_field_read_overwritten_by_constant_before_return(self):
        rows = code([0x1052, 0, 0x0012, 0x000f], ['User.proType:I'])
        self.assertEqual(['iget', 'const/4', 'return'], [r['op'] for r in rows])
        self.assertEqual('v0, v1, User.proType:I', rows[0]['args'])
        self.assertEqual('v0, 0', rows[1]['args'])
        self.assertEqual('v0', rows[2]['args'])

    def test_branch_offsets_and_signed_small_constant(self):
        rows = code([0x0038, 4, 0xf112, 0x010f, 0x0012, 0x000f])
        self.assertEqual('v0, -> 0004', rows[0]['args'])
        self.assertEqual('v1, -1', rows[1]['args'])
        self.assertEqual([0, 2, 3, 4, 5], [r['pc'] for r in rows])

    def test_invoke_registers_and_long_constant(self):
        rows = code([0x1071, 0, 2, 0x0018, 0x5900, 0xbc54, 0x09a6, 0, 0x0010],
                    methods=[('Example', 'isPro', '(LUser;)Z')])
        self.assertEqual('{v2}, Example.isPro(LUser;)Z', rows[0]['args'])
        self.assertEqual('v0, 10611728865536', rows[1]['args'])
        self.assertEqual(8, rows[2]['pc'])

    def test_array_payload_does_not_shift_following_instructions(self):
        rows = code([0x0300, 1, 3, 0, 0x0201, 3, 0x000e])
        self.assertEqual(['payload-3', 'return-void'], [r['op'] for r in rows])
        self.assertEqual(6, rows[1]['pc'])


if __name__ == '__main__': unittest.main()
