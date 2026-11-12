#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
檢查 res/layout/*.xml：每個 View 都必須「明寫」android:layout_width 與 layout_height。
（靠 style 提供 layout 參數在這個專案實測不會生效，會造成 InflateException）

用法：python3 tools/check_layouts.py <android/app/src/main/res/layout 路徑>
"""
import sys
import os
import glob
import xml.etree.ElementTree as ET

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

# 這些標籤不需要 layout 參數
NO_LAYOUT_NEEDED = {"merge", "include", "requestFocus", "tag", "Space"}


def check(path: str) -> int:
    bad = 0
    try:
        tree = ET.parse(path)
    except Exception as e:
        print(f"[XML 錯誤] {path}: {e}")
        return 1

    root = tree.getroot()
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        if tag in NO_LAYOUT_NEEDED:
            continue
        if tag == "include":
            continue

        has_w = el.get(ANDROID_NS + "layout_width") is not None
        has_h = el.get(ANDROID_NS + "layout_height") is not None
        if not (has_w and has_h):
            missing = []
            if not has_w:
                missing.append("layout_width")
            if not has_h:
                missing.append("layout_height")
            print(f"  ✗ {os.path.basename(path)} <{tag}> 缺少 {', '.join(missing)}")
            bad += 1
    return bad


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "."
    files = sorted(glob.glob(os.path.join(base, "**", "*.xml"), recursive=True))
    if not files:
        print("找不到任何 layout xml")
        return 1

    total = 0
    for f in files:
        total += check(f)

    if total:
        print(f"\n❌ 共 {total} 個元件缺少 layout 參數")
        return 1
    print(f"✅ 檢查通過（{len(files)} 個 layout 檔）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
