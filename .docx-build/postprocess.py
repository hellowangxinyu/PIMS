# -*- coding: utf-8 -*-
"""docx 页码后处理（WPS 兼容）：
1. 删除封面 section 的空 <w:pgNumType/>
2. 按 section 顺序给 footer 的 PAGE 域加显式格式开关：Roman / arabic
"""
import re, shutil, sys, zipfile

SRC = "SOP.docx"

with zipfile.ZipFile(SRC, "r") as z:
    names = z.namelist()
    contents = {n: z.read(n) for n in names}

doc = contents["word/document.xml"].decode("utf-8")

# 1) 删空 pgNumType（docx-js 对未设页码的 section 也会输出）
before = doc.count("<w:pgNumType/>")
doc = doc.replace("<w:pgNumType/>", "")
print(f"removed empty pgNumType: {before}")

# 2) 按文档顺序找 sectPr（1=封面 2=Roman 3=Arabic）
sectprs = re.findall(r"<w:sectPr[^>]*>.*?</w:sectPr>", doc, re.S)
print(f"sections found: {len(sectprs)}")
fmt_by_section = {}  # rId -> 'ROMAN'|'arabic'
for idx, sp in enumerate(sectprs, 1):
    fmt = None
    if 'w:fmt="upperRoman"' in sp:
        fmt = "ROMAN"
    elif 'w:fmt="decimal"' in sp:
        fmt = "arabic"
    if not fmt:
        continue
    for rid in re.findall(r'<w:footerReference[^>]*r:id="([^"]+)"', sp):
        fmt_by_section[rid] = fmt
print("footer refs:", fmt_by_section)

# 3) rels: rId -> footer 文件
rels = contents["word/_rels/document.xml.rels"].decode("utf-8")
rid2target = dict(re.findall(r'<Relationship[^>]*Id="([^"]+)"[^>]*Target="([^"]+)"', rels))

# 4) patch footer XML
for rid, fmt in fmt_by_section.items():
    target = rid2target.get(rid, "")
    if "footer" not in target:
        continue
    path = "word/" + target if not target.startswith("word/") else target
    if path not in contents:
        print(f"WARN: {path} not in zip")
        continue
    xml = contents[path].decode("utf-8")
    patched = re.sub(
        r"(<w:instrText[^>]*>)\s*PAGE\s*(</w:instrText>)",
        lambda m: m.group(1) + f" PAGE \\* {fmt} \\* MERGEFORMAT " + m.group(2),
        xml,
    )
    if patched != xml:
        contents[path] = patched.encode("utf-8")
        print(f"patched {path} -> \\* {fmt}")

contents["word/document.xml"] = doc.encode("utf-8")

# 5) 重新打包
shutil.move(SRC, SRC + ".bak")
with zipfile.ZipFile(SRC, "w", zipfile.ZIP_DEFLATED) as z:
    for n in names:
        z.writestr(n, contents[n])
print("repacked:", SRC)
