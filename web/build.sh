#!/bin/sh
# Build: demo (with rank images) -> app (embeds demo + portrait + rank images)
cd "$(dirname "$0")"
python3 - <<'PY'
import base64
b64 = lambda f: base64.b64encode(open(f,'rb').read()).decode()
css = "\n".join(f'.rank-bg i[data-img={n}]{{background-image:url(data:image/webp;base64,{b64(f"rank-{n}.webp")})}}' for n in ("e","d","c","b","a","s"))
d = open('demo/monark-src.html').read(); assert d.count('/*__RANK_IMG_CSS__*/')==1
open('demo/jarvis-monark-demo.html','w').write(d.replace('/*__RANK_IMG_CSS__*/', css))
s = "".join(open(f).read() for f in ['parts/1-style.html','parts/1b-levelup.css.html','parts/2-markup.html','parts/3-script.html'])
for k,v in [('/*__RANK_IMG_CSS__*/', css), ('__STUDIO_B64__', b64('demo/jarvis-monark-demo.html')), ('__HUNTER_B64__', b64('hunter.webp'))]:
    assert s.count(k)==1, k; s = s.replace(k, v)
open('site/jarvis-shadow-reminder.html','w').write(s)
PY
node -e "for(const f of ['site/jarvis-shadow-reminder.html','demo/jarvis-monark-demo.html']){const s=require('fs').readFileSync(f,'utf8'); const js=s.split('<script>').slice(1).map(x=>x.split('</script>')[0]).join('\n;\n'); new Function(js); console.log('syntax ok', f, s.length)}"
