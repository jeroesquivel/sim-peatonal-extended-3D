#!/usr/bin/env python3
"""Verificador determinista de citas de código en los .md de oral/.

Para cada bloque ```lang ... ``` cuya PRIMERA línea sea un comentario
`// ruta:inicio-fin`  (o `# ruta:inicio-fin`), comprueba que:
  1. La ruta existe.
  2. inicio/fin están dentro del archivo.
  3. Las líneas del snippet (ignorando la cabecera y los marcadores de recorte
     `// ...` / `# ...`) aparecen, en orden, dentro de la ventana [inicio, fin]
     del archivo real (subsecuencia, tolerante a recortes y a diferencias de
     indentación/espacios en blanco).

Reporta cada bloque como OK o MISMATCH con el detalle.
"""
import os
import re
import sys
import glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HEADER_RE = re.compile(r'^\s*(?://|#)\s*([\w./\-]+\.(?:java|py|csv|txt)):(\d+)(?:-(\d+))?\s*$')
FENCE_RE = re.compile(r'^```(\w+)?\s*$')

def norm(s):
    return re.sub(r'\s+', ' ', s.strip())

def is_trunc(line):
    t = line.strip()
    return t in ('// ...', '# ...', '...', '// …', '# …')

def read_file_lines(path):
    full = os.path.join(ROOT, path)
    if not os.path.exists(full):
        return None
    with open(full, encoding='utf-8') as f:
        return f.read().split('\n')

def check_block(md_file, lang, lines, blocknum):
    """lines: content lines of the fenced block (without the ``` fences)."""
    if not lines:
        return None
    m = HEADER_RE.match(lines[0])
    if not m:
        return ('NOHEADER', md_file, blocknum, lines[0][:80])
    path, start, end = m.group(1), int(m.group(2)), int(m.group(3) or m.group(2))
    flines = read_file_lines(path)
    if flines is None:
        return ('MISSINGFILE', md_file, blocknum, path)
    n = len(flines)
    if start < 1 or end > n or start > end:
        return ('BADRANGE', md_file, blocknum, f'{path}:{start}-{end} (file has {n} lines)')
    window = [norm(x) for x in flines[start-1:end] if x.strip() != '']
    snippet = [norm(x) for x in lines[1:] if x.strip() != '' and not is_trunc(x)]
    # subsequence match: every snippet line appears in order within window
    wi = 0
    misses = []
    for sl in snippet:
        found = False
        while wi < len(window):
            if window[wi] == sl:
                found = True
                wi += 1
                break
            wi += 1
        if not found:
            misses.append(sl)
    if misses:
        return ('MISMATCH', md_file, blocknum, f'{path}:{start}-{end}', misses[:4])
    return ('OK', md_file, blocknum, f'{path}:{start}-{end}', len(snippet))

def scan(md_file):
    results = []
    with open(md_file, encoding='utf-8') as f:
        text = f.read().split('\n')
    i = 0
    blocknum = 0
    while i < len(text):
        fm = FENCE_RE.match(text[i])
        if fm:
            lang = fm.group(1) or ''
            j = i + 1
            body = []
            while j < len(text) and not text[j].startswith('```'):
                body.append(text[j])
                j += 1
            blocknum += 1
            # only check code-ish langs
            if lang in ('java', 'python', 'py', 'csv', 'bash', 'text', ''):
                r = check_block(md_file, lang, body, blocknum)
                if r:
                    results.append(r)
            i = j + 1
        else:
            i += 1
    return results

def main():
    mds = sorted(glob.glob(os.path.join(ROOT, 'oral', '**', '*.md'), recursive=True))
    total_ok = total_mis = total_nohdr = 0
    for md in mds:
        rel = os.path.relpath(md, ROOT)
        rs = scan(md)
        for r in rs:
            kind = r[0]
            if kind == 'OK':
                total_ok += 1
            elif kind == 'NOHEADER':
                total_nohdr += 1
                print(f'[NOHEADER] {rel} block#{r[2]}: first line = {r[3]!r}')
            else:
                total_mis += 1
                print(f'[{kind}] {rel} block#{r[2]}: {r[3]}')
                if len(r) > 4:
                    for miss in r[4]:
                        print(f'         snippet line not found in window: {miss!r}')
    print(f'\n=== RESUMEN: {total_ok} OK, {total_mis} MISMATCH/BADRANGE/MISSING, '
          f'{total_nohdr} bloques sin cabecera de cita ===')
    return 1 if total_mis else 0

if __name__ == '__main__':
    sys.exit(main())
