#!/usr/bin/env python3
"""Reproducible essential-file snapshot for EyeAtlas VR; no patient data included.
Usage: python3 scripts/snapshot.py --phase P00 [--verify]
"""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {'CODE_DUMP.md', 'MASTER_BLUEPRINT.md', 'PROJECT_LOG.md'}
EXCLUDE_DIR = {'.git','.gradle','.idea','.venv','build','__pycache__','.pytest_cache','data','datasets','weights','models_artifacts'}
EXCLUDE_SUFFIX = {'.apk','.aab','.so','.jar','.png','.jpg','.jpeg','.webp','.bin','.onnx','.tflite','.pt','.pth','.ckpt','.zip','.pdf','.mp4','.mkv','.mov'}

def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

def paths():
    for p in sorted(ROOT.rglob('*'), key=lambda p: tuple(p.relative_to(ROOT).parts)):
        if not p.is_file() or any(x in EXCLUDE_DIR for x in p.relative_to(ROOT).parts):
            continue
        if p.name in EXCLUDED or p.suffix.lower() in EXCLUDE_SUFFIX:
            continue
        yield p

def generate(phase: str) -> str:
    allpaths = list(paths())
    entries = []
    for path in allpaths:
        rel = path.relative_to(ROOT).as_posix()
        raw = path.read_bytes()
        try:
            content = raw.decode('utf-8')
        except UnicodeDecodeError as exc:
            raise ValueError(f'Unexpected binary/source file: {rel}') from exc
        language = path.suffix.lstrip('.') or 'text'
        entries.append((rel, raw, content, language))
    manifest = '\n'.join(f'{p}\t{len(b)}\t{digest(b)}' for p,b,_,_ in entries)
    lines = [
        '# EYEATLAS VR - COMPLETE ESSENTIAL FILE SNAPSHOT',
        f'**Phase:** {phase} | **Essential files:** {len(entries)} | **Manifest SHA256:** `{digest(manifest.encode())}`',
        '',
        'The MASTER_BLUEPRINT.md and PROJECT_LOG.md are separately preserved handoff files. This is all other essential plaintext source/config/test/hardware/docs, including zero-byte reserved planned files. No omitted code and no raw patient data.',
        '', '## Canonical file manifest', '```text', manifest, '```', '',
        '## Exact file contents'
    ]
    for rel,raw,content,language in entries:
        lines += ['',f'### FILE `{rel}` | bytes={len(raw)} | SHA256=`{digest(raw)}`',f'```{language}',content.rstrip('\n'),'```',f'END FILE `{rel}`']
    return '\n'.join(lines)+'\n'

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--phase',default='pre-P00')
    ap.add_argument('--verify',action='store_true')
    args=ap.parse_args()
    target=ROOT/'CODE_DUMP.md'
    current=generate(args.phase)
    if args.verify:
        assert target.exists() and target.read_bytes().decode('utf-8') == current, 'Source code dump drift: re-run snapshot after any change'
        print('CODE_DUMP VERIFIED',digest(current.encode()))
    else:
        target.write_text(current, encoding='utf-8', newline='\n')
        print('CODE_DUMP WRITTEN',digest(current.encode()))
if __name__=='__main__':main()
