#!/usr/bin/env python3
"""Negative controls for the guide evidence gate; these are not native UI runs."""
import copy
import importlib.util
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

spec = importlib.util.spec_from_file_location('guide_gate', Path(__file__).with_name('analyze-guide-book-audit.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def png():
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 320, 240, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress((b'\0' + b'\xff' * 960) * 240)) + chunk(b'IEND', b''))


class GuideEvidenceGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.captures = Path(self.temp.name)
        self.authored = {
            ('magnetization:basics/example', 1): {'type': 'patchouli:text', 'text': 'guide.first'},
            ('magnetization:basics/example', 2): {'type': 'patchouli:crafting', 'recipe': 'magnetization:magnetic_plate'},
            ('magnetization:basics/example', 3): {'type': 'patchouli:text', 'text': 'guide.last'},
        }
        self.lang = {'guide.first': 'Open $(l:magnetization:basics/example)example$(/l).', 'guide.last': 'Last page.'}
        self.rows = []
        for phase, (locale, scale) in gate.PASSES.items():
            for spread in range(2):
                pages = []
                for index in range(spread * 2 + 1, min(spread * 2 + 3, 4)):
                    source = self.authored[('magnetization:basics/example', index)]
                    record = {'page': index, 'class': 'PageText' if 'text' in source else 'PageCrafting',
                              'definition': copy.deepcopy(source), 'titleWidth': 80}
                    if 'text' in source:
                        record.update(textScale=1.0, words=5, titleWidth=80,
                                      wordBounds={'maxX': 110, 'maxY': 140, 'widthLimit': 116, 'heightLimit': 156})
                    if phase == 0 and index == 1:
                        record['clickedLinks'] = 1
                    pages.append(record)
                name = f'fixture-{phase}-{spread}.png'
                (self.captures / name).write_bytes(png())
                self.rows.append({'pass': phase, 'locale': locale, 'guiScale': scale, 'effectiveGuiScale': float(scale),
                                  'entry': 'magnetization:basics/example', 'spread': spread,
                                  'capture': name, 'pages': pages})

    def check(self):
        return gate.verify(self.rows, self.authored, self.lang, self.captures)

    def test_complete_four_pass_fixture_is_accepted(self):
        report = self.check()
        self.assertEqual((12, 8, 1), (report['renderedPages'], report['spreads'], report['clickedLinks']))

    def test_missing_spread_is_rejected(self):
        self.rows.pop()
        with self.assertRaisesRegex(ValueError, 'Missing .* native pages'):
            self.check()

    def test_missing_effective_gui_scale_is_rejected(self):
        del self.rows[0]['effectiveGuiScale']
        with self.assertRaisesRegex(ValueError, 'effective GUI scale'):
            self.check()

    def test_missing_title_width_is_rejected(self):
        del self.rows[0]['pages'][0]['titleWidth']
        with self.assertRaisesRegex(ValueError, 'native title width'):
            self.check()

    def test_overflowing_title_is_rejected(self):
        self.rows[0]['pages'][0]['titleWidth'] = 117
        with self.assertRaisesRegex(ValueError, 'native title width'):
            self.check()

    def test_crafting_title_overflow_is_rejected(self):
        self.rows[0]['pages'][1]['titleWidth'] = 150
        with self.assertRaisesRegex(ValueError, 'native title width'):
            self.check()

    def test_clamped_gui_scale_is_rejected(self):
        self.rows[2]['effectiveGuiScale'] = 2.0
        with self.assertRaisesRegex(ValueError, 'effective GUI scale'):
            self.check()

    def test_missing_word_bounds_is_rejected(self):
        del self.rows[0]['pages'][0]['wordBounds']
        with self.assertRaisesRegex(ValueError, 'word bounds missing'):
            self.check()

    def test_horizontal_and_vertical_overflow_are_rejected(self):
        bounds = self.rows[0]['pages'][0]['wordBounds']
        for field, limit in [('maxX', 116), ('maxY', 156)]:
            with self.subTest(field=field):
                original = bounds[field]
                bounds[field] = limit + 1
                with self.assertRaisesRegex(ValueError, 'word bounds overflow'):
                    self.check()
                bounds[field] = original

    def test_invented_page_limit_is_rejected(self):
        self.rows[0]['pages'][0]['wordBounds']['heightLimit'] = 180
        with self.assertRaisesRegex(ValueError, 'page limits'):
            self.check()

    def test_nonfinite_bounds_are_rejected(self):
        self.rows[0]['pages'][0]['wordBounds']['maxX'] = float('nan')
        with self.assertRaisesRegex(ValueError, 'word bounds missing/invalid'):
            self.check()

    def test_duplicate_spread_is_rejected(self):
        self.rows.append(copy.deepcopy(self.rows[0]))
        with self.assertRaisesRegex(ValueError, 'Duplicate spread'):
            self.check()

    def test_changed_source_definition_is_rejected(self):
        self.rows[0]['pages'][0]['definition']['text'] = 'stale.translation'
        with self.assertRaisesRegex(ValueError, 'definition differs'):
            self.check()

    def test_undersized_text_is_rejected(self):
        self.rows[0]['pages'][0]['textScale'] = 0.7
        with self.assertRaisesRegex(ValueError, 'Undersized'):
            self.check()

    def test_fractional_shrink_is_rejected_even_above_old_threshold(self):
        for scale in (0.85, 0.928, 0.9354839, 0.9830508, 0.999):
            with self.subTest(scale=scale):
                self.rows[0]['pages'][0]['textScale'] = scale
                with self.assertRaisesRegex(ValueError, 'Undersized'):
                    self.check()

    def test_missing_native_text_layout_is_rejected(self):
        self.rows[0]['pages'][0]['words'] = 0
        with self.assertRaisesRegex(ValueError, 'Native text layout missing'):
            self.check()

    def test_missing_link_click_is_rejected(self):
        self.rows[0]['pages'][0]['clickedLinks'] = 0
        with self.assertRaisesRegex(ValueError, 'click coverage'):
            self.check()

    def test_repeated_locale_is_not_a_second_locale_pass(self):
        self.rows[4]['locale'] = 'en_us'
        with self.assertRaisesRegex(ValueError, 'Wrong locale'):
            self.check()

    def test_missing_capture_is_rejected(self):
        (self.captures / self.rows[0]['capture']).unlink()
        with self.assertRaisesRegex(ValueError, 'Missing native capture'):
            self.check()

    def test_non_png_capture_is_rejected(self):
        (self.captures / self.rows[0]['capture']).write_text('not an image')
        with self.assertRaisesRegex(ValueError, 'not a PNG'):
            self.check()

    def test_truncated_png_is_rejected(self):
        (self.captures / self.rows[0]['capture']).write_bytes(png()[:24])
        with self.assertRaisesRegex(ValueError, 'Truncated PNG'):
            self.check()

    def test_crafting_page_cannot_masquerade_as_text(self):
        self.rows[0]['pages'][1]['class'] = 'PageText'
        with self.assertRaisesRegex(ValueError, 'Wrong native page class'):
            self.check()


if __name__ == '__main__':
    unittest.main(verbosity=2)
