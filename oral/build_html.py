#!/usr/bin/env python3
"""Construye oral/index.html: un solo HTML indexado con TODO el blindaje.

Convierte los .md de oral/ (subconjunto controlado de Markdown) a una página
única con navegación lateral, código resaltado (pygments) y todos los
cross-links .md reescritos a anclas in-page. Autocontenido (CSS inline).

Uso:  python3 oral/build_html.py
"""
import os
import re
import html

from pygments import highlight
from pygments.lexers import get_lexer_by_name
from pygments.formatters import HtmlFormatter
from pygments.util import ClassNotFound

ORAL = os.path.dirname(os.path.abspath(__file__))

# Orden de lectura de la página.
FILES = [
    'README.md',
    'RESUMEN-BREVE.md',
    '00-resumen-ejecutivo.md',
    'resumenes/01-introduccion-observables.md',
    'resumenes/02-implementacion.md',
    'resumenes/03-simulaciones.md',
    'resumenes/04-resultados.md',
    'resumenes/05-conclusiones.md',
    'codigo/01-tipos-base-io.md',
    'codigo/02-fisica-cpm.md',
    'codigo/03-grafo.md',
    'codigo/04-vecinos-cim-geometria.md',
    'blindaje/q-tipos-io.md',
    'blindaje/q-fisica-cpm.md',
    'blindaje/q-grafo.md',
    'blindaje/q-vecinos-cim.md',
    'blindaje/q-escenario-modelado.md',
    'blindaje/q-resultados-estadistica.md',
]
FILESET = set(FILES)

CITE_RE = re.compile(r'^\s*(?://|#)\s*([\w./\-]+\.(?:java|py|csv|txt)):(\d+)(?:-(\d+))?\s*$')
HDR_RE = re.compile(r'^(#{1,4})\s+(.*?)\s*$')
EXPLICIT_ID_RE = re.compile(r'\s*\{#([\w\-]+)\}\s*$')
LINK_RE = re.compile(r'\[([^\]]+)\]\(([^)]+)\)')
CODE_SPAN_RE = re.compile(r'`([^`]+)`')
BOLD_RE = re.compile(r'\*\*([^*]+)\*\*')
ITALIC_RE = re.compile(r'(?<!\*)\*(?!\*)([^*]+)\*(?!\*)')
TABLE_SEP_RE = re.compile(r'^\s*\|?[\s:|-]+\|[\s:|-]*$')


def docslug(relpath):
    return re.sub(r'[^\w]+', '-', relpath[:-3].lower()).strip('-')


def slugify(text):
    # Estilo GitHub: minúsculas, saca formato, quita puntuación (deja unicode
    # letras/dígitos/_/espacio/-), espacios -> guiones.
    t = text.strip().lower()
    t = CODE_SPAN_RE.sub(r'\1', t)
    t = BOLD_RE.sub(r'\1', t)
    t = LINK_RE.sub(r'\1', t)
    t = t.replace('\\', '')
    t = re.sub(r'[^\w\s-]', '', t, flags=re.UNICODE)
    t = re.sub(r'\s+', '-', t)
    return t


def rewrite_href(url, srcdir):
    """Reescribe un href relativo del .md al contexto de oral/index.html."""
    if url.startswith(('http://', 'https://', 'mailto:')):
        return url
    if url == 'index.html':
        return '#top'
    frag = ''
    if '#' in url:
        url, frag = url.split('#', 1)
    if url == '':  # ancla same-file (#foo)
        return '#' + srcdir['self_anchor'] + '--' + frag if frag else '#'
    # Resolver relativo al dir del archivo fuente (dentro de oral/).
    target = os.path.normpath(os.path.join(srcdir['dir'], url))
    if target.endswith('.md'):
        if target in FILESET:
            base = 'doc-' + docslug(target)
            return '#' + base + '--' + slugify_frag(frag) if frag else '#' + base
        # .md fuera de la página: link al archivo relativo a oral/.
        return os.path.relpath(os.path.join(ORAL, target), ORAL) + (('#' + frag) if frag else '')
    # No-md (informe.tex, DECISIONES.md ya cubierto, scripts): target es relativo
    # a oral/ (puede empezar con ../ si sale de oral/). index.html vive en oral/,
    # así que target ya es la ruta correcta.
    return target + (('#' + frag) if frag else '')


def slugify_frag(frag):
    # Los fragmentos en los links ya vienen como slugs (o ids explícitos).
    return frag


def render_inline(text, srcctx):
    # 1) proteger code spans
    spans = []
    def _cs(m):
        spans.append(html.escape(m.group(1)))
        return '\x00%d\x00' % (len(spans) - 1)
    text = CODE_SPAN_RE.sub(_cs, text)
    # 2) escapar HTML
    text = html.escape(text)
    # 3) links
    def _lnk(m):
        label, url = m.group(1), m.group(2)
        return '<a href="%s">%s</a>' % (html.escape(rewrite_href(url, srcctx), quote=True), label)
    text = LINK_RE.sub(_lnk, text)
    # 4) bold / italic
    text = BOLD_RE.sub(r'<strong>\1</strong>', text)
    text = ITALIC_RE.sub(r'<em>\1</em>', text)
    # 5) restaurar code spans
    text = re.sub(r'\x00(\d+)\x00', lambda m: '<code>%s</code>' % spans[int(m.group(1))], text)
    return text


def render_code(lang, lines, out):
    cite = None
    body = lines
    if lines and CITE_RE.match(lines[0]):
        cite = lines[0].strip().lstrip('/# ').strip()
        body = lines[1:]
    code = '\n'.join(body)
    lexmap = {'py': 'python', '': 'text', 'text': 'text'}
    lname = lexmap.get(lang, lang)
    try:
        lexer = get_lexer_by_name(lname)
    except ClassNotFound:
        lexer = get_lexer_by_name('text')
    hl = highlight(code, lexer, HtmlFormatter(nowrap=True))
    out.append('<div class="code">')
    if cite:
        path = cite.split(':')[0]
        out.append('<div class="cite"><span class="citepath"><a href="%s">%s</a></span></div>'
                   % (html.escape('../' + path, quote=True), html.escape(cite)))
    out.append('<pre class="hl"><code>%s</code></pre>' % hl)
    out.append('</div>')


def render_table(rows, srcctx, out):
    def cells(line):
        line = line.strip()
        if line.startswith('|'):
            line = line[1:]
        if line.endswith('|'):
            line = line[:-1]
        return [c.strip() for c in line.split('|')]
    header = cells(rows[0])
    out.append('<table><thead><tr>')
    for c in header:
        out.append('<th>%s</th>' % render_inline(c, srcctx))
    out.append('</tr></thead><tbody>')
    for r in rows[2:]:
        out.append('<tr>')
        for c in cells(r):
            out.append('<td>%s</td>' % render_inline(c, srcctx))
        out.append('</tr>')
    out.append('</tbody></table>')


def render_list(items, srcctx, out):
    # items: list of (indent, marker_type, text). Soporta 1 nivel de anidado.
    out.append('<ul>')
    i = 0
    n = len(items)
    while i < n:
        indent, mtype, text = items[i]
        li = render_inline(text, srcctx)
        # ¿hijos anidados?
        j = i + 1
        children = []
        while j < n and items[j][0] > indent:
            children.append(items[j])
            j += 1
        out.append('<li>%s' % li)
        if children:
            render_list([(c[0], c[1], c[2]) for c in children], srcctx, out)
        out.append('</li>')
        i = j
    out.append('</ul>')


def convert(md_text, relpath):
    docid = 'doc-' + docslug(relpath)
    srcctx = {'dir': os.path.dirname(relpath), 'self_anchor': docid}
    out = ['<section class="doc" id="%s">' % docid]
    lines = md_text.split('\n')
    i, n = 0, len(lines)
    headings = []  # (level, text, anchor)
    while i < n:
        line = lines[i]
        # comentarios HTML
        if line.strip().startswith('<!--'):
            while i < n and '-->' not in lines[i]:
                i += 1
            i += 1
            continue
        # code fence
        if line.startswith('```'):
            lang = line[3:].strip()
            body = []
            i += 1
            while i < n and not lines[i].startswith('```'):
                body.append(lines[i])
                i += 1
            i += 1
            render_code(lang, body, out)
            continue
        # heading
        hm = HDR_RE.match(line)
        if hm:
            level = len(hm.group(1))
            text = hm.group(2)
            em = EXPLICIT_ID_RE.search(text)
            if em:
                base = em.group(1)
                text = EXPLICIT_ID_RE.sub('', text)
            else:
                base = slugify(text)
            anchor = docid + '--' + base
            headings.append((level, text, anchor))
            out.append('<h%d id="%s">%s</h%d>' % (level, anchor, render_inline(text, srcctx), level))
            i += 1
            continue
        # hr
        if re.match(r'^---+\s*$', line):
            out.append('<hr>')
            i += 1
            continue
        # table
        if line.lstrip().startswith('|') and i + 1 < n and TABLE_SEP_RE.match(lines[i + 1]):
            tbl = [line]
            i += 1
            while i < n and lines[i].lstrip().startswith('|'):
                tbl.append(lines[i])
                i += 1
            render_table(tbl, srcctx, out)
            continue
        # blockquote
        if line.startswith('>'):
            bq = []
            while i < n and lines[i].startswith('>'):
                bq.append(lines[i][1:].lstrip())
                i += 1
            out.append('<blockquote>%s</blockquote>' % render_inline(' '.join(bq), srcctx))
            continue
        # list
        lm = re.match(r'^(\s*)([-*]|\d+\.)\s+(.*)$', line)
        if lm:
            items = []
            while i < n:
                lm = re.match(r'^(\s*)([-*]|\d+\.)\s+(.*)$', lines[i])
                if not lm:
                    if lines[i].strip() == '':
                        break
                    # continuación de item
                    if items:
                        items[-1] = (items[-1][0], items[-1][1], items[-1][2] + ' ' + lines[i].strip())
                        i += 1
                        continue
                    break
                indent = len(lm.group(1))
                items.append((indent, lm.group(2), lm.group(3)))
                i += 1
            render_list(items, srcctx, out)
            continue
        # blank
        if line.strip() == '':
            i += 1
            continue
        # paragraph
        para = [line]
        i += 1
        while i < n and lines[i].strip() != '' and not lines[i].startswith(('```', '#', '>', '|', '---')) \
                and not re.match(r'^(\s*)([-*]|\d+\.)\s+', lines[i]):
            para.append(lines[i])
            i += 1
        out.append('<p>%s</p>' % render_inline(' '.join(para), srcctx))
    out.append('</section>')
    return '\n'.join(out), docid, headings


def main():
    docs = []
    for rel in FILES:
        path = os.path.join(ORAL, rel)
        if not os.path.exists(path):
            print('WARN: falta', rel)
            continue
        with open(path, encoding='utf-8') as f:
            body_html, docid, headings = convert(f.read(), rel)
        title = headings[0][1] if headings else rel
        docs.append({'rel': rel, 'id': docid, 'title': title,
                     'headings': headings, 'html': body_html})

    # Sidebar TOC: agrupado por carpeta.
    groups = [('Inicio', ['README.md', 'RESUMEN-BREVE.md', '00-resumen-ejecutivo.md']),
              ('Resúmenes del informe', [f for f in FILES if f.startswith('resumenes/')]),
              ('Documentación de código', [f for f in FILES if f.startswith('codigo/')]),
              ('Blindaje (Q&amp;A)', [f for f in FILES if f.startswith('blindaje/')])]
    by_rel = {d['rel']: d for d in docs}
    nav = ['<nav id="toc">',
           '<a class="masthead" href="#top">'
           '<span class="mh-kicker">Simulación de Sistemas · ITBA</span>'
           '<span class="mh-title">Blindaje<br>del oral</span>'
           '<span class="mh-sub">Ampliación a 3D de un simulador peatonal · Grupo 5</span>'
           '</a>']
    for gname, rels in groups:
        nav.append('<div class="toc-group"><div class="toc-group-name">%s</div>' % gname)
        for rel in rels:
            d = by_rel.get(rel)
            if not d:
                continue
            nav.append('<a class="toc-doc" href="#%s">%s</a>' % (d['id'], html.escape(d['title'])))
            subs = [h for h in d['headings'] if h[0] == 2]
            if subs:
                nav.append('<div class="toc-subs">')
                for lvl, text, anchor in subs:
                    label = re.sub(r'`|\*\*|\\', '', text)
                    nav.append('<a class="toc-sub" href="#%s">%s</a>' % (anchor, html.escape(label)))
                nav.append('</div>')
        nav.append('</div>')
    nav.append('</nav>')

    pyg_css = HtmlFormatter(style='tango').get_style_defs('.hl')
    body = '\n'.join(d['html'] for d in docs)
    doc = (TEMPLATE
           .replace('/*PYGCSS*/', pyg_css)
           .replace('<!--NAV-->', '\n'.join(nav))
           .replace('<!--BODY-->', body))
    outpath = os.path.join(ORAL, 'index.html')
    with open(outpath, 'w', encoding='utf-8') as f:
        f.write(doc)
    print('OK ->', outpath, '(%d documentos, %d KB)' % (len(docs), len(doc) // 1024))


TEMPLATE = r'''<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Blindaje oral · TP Final SdS · Grupo 5</title>
<style>
@import url('https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,500;9..144,600;9..144,700&family=Newsreader:ital,opsz,wght@0,6..72,400;0,6..72,500;0,6..72,600;1,6..72,400;1,6..72,500&display=swap');

:root {
  --paper:#f7f3ea; --paper-2:#f1ebdd; --code-bg:#f4efe3; --cite-bg:#ece3d1;
  --ink:#23201a; --ink-soft:#575149; --faint:#8f897b;
  --rule:#e5ddcc; --rule-2:#d2c8b1;
  --accent:#8a2b26; --accent-2:#a8524a; --mark:#efe0bf;
  --display:'Fraunces','New York',ui-serif,'Iowan Old Style','Palatino Linotype',Palatino,Georgia,serif;
  --serif:'Newsreader','New York',ui-serif,Charter,'Iowan Old Style',Georgia,serif;
  --mono:ui-monospace,'SF Mono','JetBrains Mono',Menlo,Consolas,monospace;
}
* { box-sizing:border-box; }
html { scroll-behavior:smooth; }
body { margin:0; background:var(--paper); color:var(--ink);
  font-family:var(--serif); font-size:18px; line-height:1.72;
  font-feature-settings:"kern" 1,"liga" 1,"onum" 1;
  -webkit-font-smoothing:antialiased; text-rendering:optimizeLegibility;
  animation:fade .6s ease both; }
@keyframes fade { from { opacity:0 } to { opacity:1 } }
#top { position:absolute; top:0; }
::selection { background:var(--mark); }

.layout { display:grid; grid-template-columns:19rem minmax(0,1fr); align-items:start; }

/* ---------- sidebar / índice ---------- */
#toc { position:sticky; top:0; height:100vh; overflow-y:auto;
  background:var(--paper-2); border-right:1px solid var(--rule-2);
  padding:2.4rem 1.5rem 5rem; font-size:.86rem; }
.masthead { display:block; text-decoration:none; color:var(--ink);
  padding-bottom:1.4rem; margin-bottom:1.2rem; border-bottom:1px solid var(--rule-2); }
.mh-kicker { display:block; font-family:var(--serif); font-size:.66rem;
  letter-spacing:.18em; text-transform:uppercase; color:var(--accent); margin-bottom:.7rem; }
.mh-title { display:block; font-family:var(--display); font-weight:600;
  font-size:2.15rem; line-height:1.0; letter-spacing:-.01em; font-optical-sizing:auto; }
.mh-sub { display:block; font-size:.8rem; color:var(--faint);
  font-style:italic; margin-top:.7rem; line-height:1.4; }
.toc-group-name { font-family:var(--serif); text-transform:uppercase;
  font-size:.65rem; letter-spacing:.16em; color:var(--faint); margin:1.7rem .2rem .5rem; }
.toc-doc { display:block; color:var(--ink); text-decoration:none;
  padding:.28rem .55rem; border-left:2px solid transparent; font-weight:500;
  line-height:1.3; transition:color .2s,border-color .2s; }
.toc-doc:hover { color:var(--accent); }
.toc-subs { margin:.15rem 0 .55rem .55rem; border-left:1px solid var(--rule); }
.toc-sub { display:block; color:var(--ink-soft); text-decoration:none;
  padding:.16rem .55rem; font-size:.79rem; line-height:1.3;
  border-left:2px solid transparent; margin-left:-1px; transition:color .2s,border-color .2s; }
.toc-sub:hover { color:var(--accent); }
#toc a.active { color:var(--accent); border-left-color:var(--accent); font-weight:600; }

/* ---------- columna principal ---------- */
main { padding:4.5rem 4.5rem 12rem; max-width:52rem; }
.doc { padding:3.4rem 0 1rem; border-top:1px solid var(--rule); }
.doc:first-child { border-top:none; padding-top:1.2rem; }
h1,h2,h3,h4 { scroll-margin-top:1.5rem; }
h1 { font-family:var(--display); font-weight:600; font-size:2.55rem;
  line-height:1.06; letter-spacing:-.016em; color:var(--ink);
  margin:.4rem 0 1.6rem; font-optical-sizing:auto; }
h2 { font-family:var(--display); font-weight:600; font-size:1.6rem;
  line-height:1.15; color:var(--ink); margin:2.9rem 0 1rem;
  padding-bottom:.5rem; border-bottom:1px solid var(--rule); font-optical-sizing:auto; }
h3 { font-family:var(--display); font-weight:500; font-size:1.24rem;
  color:var(--ink); margin:2rem 0 .6rem; }
h4 { font-family:var(--serif); font-weight:600; font-size:.82rem;
  text-transform:uppercase; letter-spacing:.07em; color:var(--faint); margin:1.5rem 0 .5rem; }
p { margin:.9rem 0; max-width:40rem; }
a { color:var(--accent); text-decoration:underline;
  text-decoration-color:var(--rule-2); text-underline-offset:2px;
  text-decoration-thickness:.06em; transition:text-decoration-color .2s; }
a:hover { text-decoration-color:var(--accent); }
strong { font-weight:600; color:var(--ink); }
em { font-style:italic; }
code { font-family:var(--mono); font-size:.8em; background:var(--paper-2);
  color:#7a3b1c; padding:.06em .35em; border-radius:3px; border:1px solid var(--rule); }
hr { border:none; height:1.6rem; margin:2.2rem auto; max-width:40rem; text-align:center; }
hr::before { content:"\00A7"; color:var(--rule-2); font-family:var(--display); font-size:1.15rem; }
blockquote { margin:1.3rem 0; padding:.15rem 0 .15rem 1.3rem; max-width:40rem;
  border-left:2px solid var(--accent); color:var(--ink-soft); font-style:italic; }
blockquote strong { color:var(--accent); font-style:normal; }
blockquote code { font-style:normal; }
ul { margin:.7rem 0; padding-left:1.3rem; max-width:40rem; }
li { margin:.35rem 0; }
li::marker { color:var(--accent-2); }

/* ---------- tablas: estilo booktabs ---------- */
table { border-collapse:collapse; width:100%; max-width:42rem; margin:1.5rem 0;
  font-size:.9rem; display:block; overflow-x:auto;
  font-variant-numeric:tabular-nums lining-nums; }
thead th { border-top:1.5px solid var(--ink); border-bottom:1px solid var(--rule-2);
  background:none; color:var(--ink); font-family:var(--serif); font-weight:600;
  text-align:left; padding:.5rem .85rem; }
tbody td { border:none; border-bottom:1px solid var(--rule);
  padding:.45rem .85rem; text-align:left; vertical-align:top; color:var(--ink-soft); }
tbody tr:last-child td { border-bottom:1.5px solid var(--ink); }
tbody td:first-child { color:var(--ink); }

/* ---------- código y citas ---------- */
.code { margin:1.3rem 0; max-width:44rem; border:1px solid var(--rule-2);
  border-radius:3px; overflow:hidden; background:var(--code-bg); }
.cite { padding:.42rem .9rem; border-bottom:1px solid var(--rule-2);
  background:var(--cite-bg); font-family:var(--mono); font-size:.72rem; color:var(--ink-soft); }
.cite::before { content:"fuente"; text-transform:uppercase; letter-spacing:.11em;
  color:var(--accent); font-family:var(--serif); font-weight:600; font-size:.68rem; margin-right:.6rem; }
.cite .citepath a { color:var(--ink-soft); text-decoration:none; }
.cite .citepath a:hover { color:var(--accent); }
pre.hl { margin:0; padding:.95rem 1.1rem; background:var(--code-bg); overflow-x:auto;
  font-family:var(--mono); font-size:.8rem; line-height:1.6; }
pre.hl code { background:none; color:var(--ink); padding:0; border:none; font-size:1em; }
/*PYGCSS*/
.hl { background:var(--code-bg); }

/* ---------- lettrine en el párrafo de apertura ---------- */
#doc-readme > p:first-of-type::first-letter {
  font-family:var(--display); font-weight:600; float:left; font-size:3.6rem;
  line-height:.72; padding:.32rem .5rem 0 0; color:var(--accent); }

/* ---------- Q&A del blindaje: separar cada par ---------- */
[id^="doc-blindaje"] h3 { border-top:1px solid var(--rule); padding-top:1.4rem;
  color:var(--accent); font-weight:600; font-family:var(--display); }

@media (max-width:860px) {
  .layout { grid-template-columns:1fr; }
  #toc { position:static; height:auto; border-right:none;
    border-bottom:1px solid var(--rule-2); }
  main { padding:2.4rem 1.5rem 6rem; max-width:none; }
  h1 { font-size:2rem; }
}
</style>
</head>
<body>
<span id="top"></span>
<div class="layout">
<!--NAV-->
<main>
<!--BODY-->
</main>
</div>
<script>
(function(){
  var links = Array.prototype.slice.call(document.querySelectorAll('#toc a[href^="#"]'));
  var map = {};
  links.forEach(function(a){ map[a.getAttribute('href').slice(1)] = a; });
  var targets = Array.prototype.slice.call(document.querySelectorAll('main [id]'))
    .filter(function(el){ return map[el.id]; });
  var active = null;
  function setActive(id){
    if (active === id) return;
    active = id;
    links.forEach(function(a){ a.classList.remove('active'); });
    var a = map[id];
    if (a) { a.classList.add('active'); a.scrollIntoView({ block:'nearest' }); }
  }
  var obs = new IntersectionObserver(function(entries){
    entries.forEach(function(e){ if (e.isIntersecting) setActive(e.target.id); });
  }, { rootMargin:'0px 0px -78% 0px', threshold:0 });
  targets.forEach(function(t){ obs.observe(t); });

  // Navegación robusta: smooth-scroll al ancla y deep-link en la URL.
  document.addEventListener('click', function(ev){
    var a = ev.target.closest ? ev.target.closest('a[href^="#"]') : null;
    if (!a) return;
    var id = a.getAttribute('href').slice(1);
    if (!id) return;
    var el = document.getElementById(id);
    if (!el) return;
    ev.preventDefault();
    el.scrollIntoView({ behavior:'smooth', block:'start' });
    if (history.pushState) history.pushState(null, '', '#' + id);
  });
})();
</script>
</body>
</html>
'''


if __name__ == '__main__':
    main()
