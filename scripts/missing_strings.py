#!/usr/bin/env python3
"""List string keys in res/values/strings*.xml that a locale folder lacks.

Usage: scripts/missing_strings.py [--keys] [locale-folder ...]
"""
import glob, os, sys
import xml.etree.ElementTree as ET

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
TAGS = ("string", "plurals", "string-array")


def keys(folder):
    out = {}
    for f in sorted(glob.glob(os.path.join(RES, folder, "strings*.xml"))):
        for el in ET.parse(f).getroot():
            if el.tag in TAGS and el.get("translatable") != "false":
                out[el.get("name")] = os.path.basename(f)
    return out


def main(argv):
    show = "--keys" in argv
    want = [a for a in argv if not a.startswith("--")]
    base = keys("values")
    folders = want or sorted(
        os.path.basename(d) for d in glob.glob(os.path.join(RES, "values-*"))
        if glob.glob(os.path.join(d, "strings*.xml")))
    for d in folders:
        have = keys(d)
        miss = [k for k in base if k not in have]
        print(f"{d}: {len(miss)} missing of {len(base)}")
        if show:
            for k in miss:
                print(f"  {base[k]}  {k}")


if __name__ == "__main__":
    main(sys.argv[1:])
