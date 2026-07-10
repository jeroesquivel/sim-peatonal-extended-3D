#!/usr/bin/env python3
"""Genera PDFs compactos del blindaje (pandoc -> xelatex) y los deja en ~/Downloads.

- Layout denso: extarticle 9pt, márgenes 1.4 cm, espaciado ajustado, código con
  wrap. Fuente STIXGeneral (cobertura total de símbolos) + Menlo para código.
- Sanea los pocos glyphs que xelatex no puede (emoji), quita los bloques de
  "Navegación" (links relativos, inútiles en PDF) y comprime con ghostscript.

Uso:  python3 oral/build_pdf.py
"""
import os
import re
import subprocess
import tempfile

ORAL = os.path.dirname(os.path.abspath(__file__))
DOWNLOADS = os.path.expanduser('~/Downloads')

SECTIONS = {
    'SdS-Oral-Grupo5-1-Resumen-breve': ['RESUMEN-BREVE.md', '00-resumen-ejecutivo.md'],
    'SdS-Oral-Grupo5-2-Resumenes': [
        'resumenes/01-introduccion-observables.md',
        'resumenes/02-implementacion.md',
        'resumenes/03-simulaciones.md',
        'resumenes/04-resultados.md',
        'resumenes/05-conclusiones.md',
    ],
    'SdS-Oral-Grupo5-3-Codigo': [
        'codigo/01-tipos-base-io.md',
        'codigo/02-fisica-cpm.md',
        'codigo/03-grafo.md',
        'codigo/04-vecinos-cim-geometria.md',
    ],
    'SdS-Oral-Grupo5-4-Blindaje': [
        'blindaje/q-tipos-io.md',
        'blindaje/q-fisica-cpm.md',
        'blindaje/q-grafo.md',
        'blindaje/q-vecinos-cim.md',
        'blindaje/q-escenario-modelado.md',
        'blindaje/q-resultados-estadistica.md',
    ],
}

TITLES = {
    'SdS-Oral-Grupo5-1-Resumen-breve': 'Blindaje oral · Resumen breve',
    'SdS-Oral-Grupo5-2-Resumenes': 'Blindaje oral · Resúmenes del informe',
    'SdS-Oral-Grupo5-3-Codigo': 'Blindaje oral · Documentación de código',
    'SdS-Oral-Grupo5-4-Blindaje': 'Blindaje oral · Preguntas y respuestas',
}

HEADER = r'''
\usepackage{fvextra}
\fvset{breaklines=true,breakanywhere=true,fontsize=\footnotesize}
\usepackage{enumitem}
\setlist{nosep,leftmargin=1.3em,topsep=2pt,parsep=1pt}
\setlength{\parskip}{2.5pt}
\setlength{\parindent}{0pt}
\usepackage{titlesec}
\titlespacing*{\section}{0pt}{9pt}{3pt}
\titlespacing*{\subsection}{0pt}{6pt}{2pt}
\titlespacing*{\subsubsection}{0pt}{5pt}{1pt}
\titleformat*{\section}{\large\bfseries}
\titleformat*{\subsection}{\normalsize\bfseries}
\titleformat*{\subsubsection}{\small\bfseries}
\AtBeginDocument{\hypersetup{hidelinks}}
'''


def sanitize(md):
    md = md.replace('️', '')            # variation selector-16
    md = md.replace('\U0001F310', '')        # 🌐 globe
    md = md.replace('➡', '→')      # ➡ -> →
    md = md.replace('⬅', '←')      # ⬅ -> ←
    md = md.replace('₂', '2')           # ₂ -> 2
    # quitar el bloque final de "Navegación" (y su regla horizontal previa)
    md = re.sub(r'\n(?:-{3,}\s*\n)?#{1,3}\s+Navegaci[oó]n\b.*$', '\n', md,
                flags=re.S | re.I)
    # quitar comentarios HTML de coordinación
    md = re.sub(r'<!--.*?-->', '', md, flags=re.S)
    return md.strip() + '\n'


def build_section(name, files):
    parts = []
    for i, rel in enumerate(files):
        path = os.path.join(ORAL, rel)
        if not os.path.exists(path):
            print('  WARN falta', rel)
            continue
        with open(path, encoding='utf-8') as f:
            body = sanitize(f.read())
        if i > 0:
            parts.append('\n\n\\newpage\n\n')
        parts.append(body)
    md = ''.join(parts)

    with tempfile.TemporaryDirectory() as td:
        md_path = os.path.join(td, 'in.md')
        hdr_path = os.path.join(td, 'header.tex')
        raw_pdf = os.path.join(td, 'raw.pdf')
        with open(md_path, 'w', encoding='utf-8') as f:
            f.write(md)
        with open(hdr_path, 'w', encoding='utf-8') as f:
            f.write(HEADER)

        cmd = [
            'pandoc', md_path, '-o', raw_pdf,
            '--pdf-engine=xelatex',
            '-V', 'documentclass=extarticle',
            '-V', 'fontsize=9pt',
            '-V', 'geometry:a4paper',
            '-V', 'geometry:margin=1.4cm',
            '-V', 'mainfont=STIXGeneral',
            '-V', 'monofont=Menlo',
            '-V', 'monofontoptions=Scale=0.80',
            '-V', 'colorlinks=false',
            '--highlight-style=tango',
            '-H', hdr_path,
            '--metadata', 'title=' + TITLES[name],
        ]
        r = subprocess.run(cmd, capture_output=True, text=True)
        if r.returncode != 0 or not os.path.exists(raw_pdf):
            print('  ERROR pandoc en', name)
            print(r.stderr[-1500:])
            return None

        out_pdf = os.path.join(DOWNLOADS, name + '.pdf')
        gz_pdf = os.path.join(td, 'gz.pdf')
        gs = subprocess.run([
            'gs', '-sDEVICE=pdfwrite', '-dCompatibilityLevel=1.5',
            '-dPDFSETTINGS=/ebook', '-dEmbedAllFonts=true', '-dSubsetFonts=true',
            '-dNOPAUSE', '-dBATCH', '-dQUIET', '-sOutputFile=' + gz_pdf, raw_pdf,
        ], capture_output=True, text=True)

        raw_sz = os.path.getsize(raw_pdf)
        best = raw_pdf
        if gs.returncode == 0 and os.path.exists(gz_pdf) and os.path.getsize(gz_pdf) < raw_sz:
            best = gz_pdf
        with open(best, 'rb') as src, open(out_pdf, 'wb') as dst:
            dst.write(src.read())
        return out_pdf, os.path.getsize(out_pdf), raw_sz


def pages(pdf):
    try:
        out = subprocess.run(['gs', '-q', '-dNODISPLAY', '-dBATCH', '-dNOPAUSE',
                              '-c', f'({pdf}) (r) file runpdfbegin pdfpagecount = quit'],
                             capture_output=True, text=True)
        return out.stdout.strip()
    except Exception:
        return '?'


def main():
    print('Generando PDFs en', DOWNLOADS)
    for name, files in SECTIONS.items():
        res = build_section(name, files)
        if res:
            path, sz, raw = res
            print(f'  OK  {os.path.basename(path):40s} {sz//1024:4d} KB '
                  f'({pages(path)} pág; raw {raw//1024} KB)')


if __name__ == '__main__':
    main()
