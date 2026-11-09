"""Fixture tests for the CI resource checker; no app build or network required."""

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "check_translations.py"
SPEC = importlib.util.spec_from_file_location("check_translations", SCRIPT)
checker = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = checker
SPEC.loader.exec_module(checker)


def unit(value, state="translated"):
    return {"stringUnit": {"value": value, "state": state}}


class TranslationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write(self, path, text):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text)

    def apple(self, en=None, es=None, **attributes):
        localizations = {k: v for k, v in (("en", en), ("es", es)) if v is not None}
        self.write("secant/Resources/Localizable.xcstrings", json.dumps({
            "sourceLanguage": "en", "strings": {"hello": {"localizations": localizations, **attributes}}
        }))
        return checker.apple_issues(self.root)

    def android(self, english, spanish=None):
        self.write("ui-lib/build.gradle.kts", "")
        self.write("ui-lib/src/main/res/values/strings.xml", f"<resources>{english}</resources>")
        if spanish is not None:
            self.write("ui-lib/src/main/res/values-es/strings.xml", f"<resources>{spanish}</resources>")
        else:
            (self.root / "ui-lib/src/main/res/values-es/strings.xml").unlink(missing_ok=True)
        return checker.android_issues(self.root)

    def test_positional_formatting_can_reorder_and_repeat_arguments(self):
        self.assertTrue(checker.compatible("%@ %lld", "%2$lld %1$@ %1$@"))
        self.assertFalse(checker.compatible("%@ %lld", "%1$lld %2$@"))
        self.assertFalse(checker.compatible("%@", "plain text"))
        self.assertFalse(checker.compatible("plain text", "%@"))
        self.assertTrue(checker.compatible("100%% %@", "%1$@ 100%%"))
        self.assertFalse(checker.compatible("%lld", "%d"))

    def test_android_relative_formatting_and_date_arguments(self):
        self.assertTrue(checker.compatible("%1$s %2$d", "%2$d %1$s %<s", android=True))
        self.assertTrue(checker.compatible("%1$tY %1$tm", "%1$td %1$tY", android=True))
        self.assertFalse(checker.compatible("%s", "%d", android=True))
        self.assertTrue(checker.compatible("%n%s", "%s%n", android=True))

    def test_dynamic_width_and_precision_consume_arguments(self):
        self.assertTrue(checker.compatible("%*.*f", "%3$*1$.*2$f"))
        self.assertFalse(checker.compatible("%*.*f", "%f"))

    def test_percentages_in_prose_are_not_format_arguments(self):
        self.assertTrue(checker.compatible("100% complete", "100% completado"))
        self.assertTrue(checker.compatible("12% of the total", "12% del total"))
        self.assertFalse(checker.compatible("Hello", "%1$Q"))

    def test_apple_missing_empty_and_unreviewed_units(self):
        self.assertEqual({i.defect for i in self.apple(unit("Hello"))}, {"missing"})
        self.assertEqual({i.defect for i in self.apple(unit("Hello"), unit(" "))}, {"empty"})
        self.assertEqual({i.defect for i in self.apple(unit("Hello"), unit("Hola", "needs_review"))}, {"unreviewed"})
        self.assertEqual(self.apple(unit("Hello"), unit("Hola")), set())

    def test_apple_nontranslatable_entry_is_excluded(self):
        self.assertEqual(self.apple(unit("ZEC"), shouldTranslate=False), set())

    def test_apple_each_plural_and_device_variant_is_checked(self):
        english = {"variations": {"plural": {"one": unit("%lld item"), "other": unit("%lld items")}}}
        spanish = {"variations": {"plural": {"one": unit("%lld elemento"), "other": unit("%@ elementos")}}}
        issues = self.apple(english, spanish)
        self.assertEqual([(i.defect, i.variant) for i in issues], [("format", "plural:other")])
        spanish = {"variations": {"device": {"mac": unit("%@", "new"), "other": unit("%lld")}}}
        issues = self.apple(unit("%lld"), spanish)
        self.assertEqual({(i.defect, i.variant) for i in issues}, {("format", "device:mac"), ("unreviewed", "device:mac")})

    def test_apple_device_default_is_a_valid_fallback(self):
        english = {"variations": {"device": {"mac": unit("Vault %@"), "other": unit("Wallet %@")}}}
        self.assertEqual(self.apple(english, unit("Billetera %@")), set())
        english["variations"]["device"]["mac"] = unit("Vault %lld")
        self.assertEqual({i.variant for i in self.apple(english, unit("Billetera %@"))}, {"device:mac"})

    def test_apple_malformed_plural_fails_instead_of_silently_skipping(self):
        with self.assertRaises(ValueError):
            self.apple(unit("Hello"), {"variations": {"plural": {"one": unit("Hola")}}})

    def test_apple_plural_cannot_be_replaced_with_an_unvarying_string(self):
        english = {"variations": {"plural": {"one": unit("%lld item"), "other": unit("%lld items")}}}
        self.assertEqual({i.defect for i in self.apple(english, unit("%lld elementos"))}, {"missing-plural"})

    def test_apple_substitution_plural_units_and_argument_types_are_checked(self):
        def plural(value):
            return {**unit("%#@count@"), "substitutions": {"count": {
                "argNum": 1, "formatSpecifier": "lld",
                "variations": {"plural": {"one": unit("%lld item"), "other": unit(value)}}
            }}}
        self.assertEqual(self.apple(plural("%lld items"), plural("%lld elementos")), set())
        issues = self.apple(plural("%lld items"), plural("%@ elementos"))
        self.assertEqual({(i.defect, i.variant) for i in issues}, {("format", "substitution:count/plural:other")})

    def test_android_missing_empty_excluded_and_styled_values(self):
        self.assertEqual({i.defect for i in self.android('<string name="hello">Hello</string>')}, {"missing"})
        self.assertEqual({i.defect for i in self.android('<string name="hello">Hello</string>', '<string name="hello"> </string>')}, {"empty"})
        self.assertEqual(self.android('<string name="token" translatable="false">ZEC</string>'), set())
        self.assertEqual(self.android('<string name="hello">Hello %1$s</string>', '<string name="hello">Hola <b>%1$s</b></string>'), set())

    def test_android_plural_other_and_format_types(self):
        en = '<plurals name="count"><item quantity="one">%d item</item><item quantity="other">%d items</item></plurals>'
        es = '<plurals name="count"><item quantity="one">%s elemento</item></plurals>'
        self.assertEqual({i.defect for i in self.android(en, es)}, {"format", "missing-other"})

    def test_android_array_lengths_are_checked(self):
        en = '<string-array name="list"><item>A</item><item>B</item></string-array>'
        es = '<string-array name="list"><item>A</item></string-array>'
        self.assertEqual({i.defect for i in self.android(en, es)}, {"array-size"})

    def test_android_configured_resource_directories_are_checked(self):
        self.write("ui-lib/build.gradle.kts", 'res.setSrcDirs(setOf("src/main/res/ui/common", "src/main/res/ui/send"))')
        for area in ("common", "send"):
            self.write(f"ui-lib/src/main/res/ui/{area}/values/strings.xml", f'<resources><string name="{area}">Hello</string></resources>')
            self.write(f"ui-lib/src/main/res/ui/{area}/values-es/strings.xml", f'<resources><string name="{area}">Hola</string></resources>')
        self.write("ui-lib/src/main/res/unused/values/strings.xml", '<resources><string name="unused">Not packaged</string></resources>')
        self.assertEqual(checker.android_issues(self.root), set())

    def test_android_nonformatted_strings_and_missing_english_source(self):
        self.assertEqual(self.android('<string name="literal" formatted="false">%s</string>', '<string name="literal">literal</string>'), set())
        issues = self.android('<string name="hello">Hello</string>', '<string name="hello">Hola</string><string name="orphan">Otro</string>')
        self.assertEqual({i.defect for i in issues}, {"missing-source"})

    def test_android_unknown_resource_configuration_fails_closed(self):
        self.write("ui-lib/build.gradle.kts", 'res.setSrcDirs(resourcePaths)')
        self.write("ui-lib/src/main/res/values/strings.xml", "<resources/>")
        with self.assertRaises(ValueError):
            checker.android_issues(self.root)

    def test_android_overlay_uses_main_locale_fallback(self):
        self.android('<string name="hello">Hello %s</string>', '<string name="hello">Hola %s</string>')
        self.write("ui-lib/src/debug/res/values/strings.xml", '<resources><string name="hello">Debug %s</string></resources>')
        self.assertEqual(checker.android_issues(self.root), set())
        self.write("ui-lib/src/debug/res/values-es/strings.xml", '<resources><string name="hello">Debug %d</string></resources>')
        self.assertEqual({i.defect for i in checker.android_issues(self.root)}, {"format"})

    def test_both_catalogs_are_checked(self):
        self.apple(unit("Hello"), unit("Hola"))
        self.write("secant/Resources/Bridge.xcstrings", json.dumps({"sourceLanguage": "en", "strings": {"bridge": {"localizations": {"en": unit("Bridge")}}}}))
        self.assertEqual({i.key for i in checker.apple_issues(self.root)}, {"bridge"})

    def test_baseline_never_masks_new_debt_or_retains_resolved_debt(self):
        old = checker.Issue("catalog", "old", "es", "missing")
        new = checker.Issue("catalog", "new", "es", "missing")
        self.assertEqual(checker.compare_baseline({old, new}, {old}), ({new}, set()))
        self.assertEqual(checker.compare_baseline(set(), {old}), (set(), {old}))
        self.assertEqual(checker.compare_baseline({old}, {old}), (set(), set()))

    def test_missing_resources_fail_closed(self):
        with self.assertRaises(ValueError):
            checker.apple_issues(self.root)
        with self.assertRaises(ValueError):
            checker.android_issues(self.root)


if __name__ == "__main__":
    unittest.main()
