import type { Context, Config } from '@netlify/edge-functions';

const ORIGIN = 'https://ibrahim-ai-y1xmj0.v2.appdeploy.ai';

function rewriteLocation(value: string | null, publicOrigin: string) {
  if (!value) return value;
  if (value.startsWith(ORIGIN)) return publicOrigin + value.slice(ORIGIN.length);
  return value;
}

function cleanResponseHeaders(source: Headers, publicOrigin: string) {
  const headers = new Headers(source);
  const location = rewriteLocation(headers.get('location'), publicOrigin);
  if (location) headers.set('location', location);
  headers.delete('content-length');
  headers.delete('content-encoding');
  return headers;
}

function startupPatch() {
  return `<script id="ibrahim-v20-shell">(()=>{const run=()=>{if(sessionStorage.getItem('ibrahimFreshBoot')==='1')return true;const b=document.querySelector('.new-chat');if(!b)return false;b.click();sessionStorage.setItem('ibrahimFreshBoot','1');return true};let n=0;const t=setInterval(()=>{n++;if(run()||n>32)clearInterval(t)},250);const brand=()=>{const hs=[...document.querySelectorAll('h1')].find(x=>(x.textContent||'').trim()==='İbrahim AI');if(!hs)return false;const p=hs.parentElement?.querySelector('p');if(p&&(p.textContent||'').trim()==='Kendi karar verir')p.textContent='Öğrenir · araştırır · analiz eder';return true};let m=0;const bt=setInterval(()=>{m++;if(brand()||m>32)clearInterval(bt)},250);window.addEventListener('pageshow',e=>{if(e.persisted)sessionStorage.removeItem('ibrahimFreshBoot')});})();</script>`;
}

export default async (req: Request, _context: Context) => {
  const incoming = new URL(req.url);
  const target = new URL(incoming.pathname + incoming.search, ORIGIN);
  const headers = new Headers(req.headers);
  headers.delete('host');
  headers.set('x-forwarded-host', incoming.host);
  headers.set('x-forwarded-proto', 'https');

  const init: RequestInit = {
    method: req.method,
    headers,
    redirect: 'manual'
  };
  if (req.method !== 'GET' && req.method !== 'HEAD') init.body = req.body;

  try {
    const upstream = await fetch(target, init);
    const responseHeaders = cleanResponseHeaders(upstream.headers, incoming.origin);
    const type = upstream.headers.get('content-type') || '';

    if (req.method === 'GET' && type.includes('text/html')) {
      const html = await upstream.text();
      const marker = startupPatch();
      const patched = html.includes('</body>') ? html.replace('</body>', `${marker}</body>`) : `${html}${marker}`;
      responseHeaders.set('cache-control', 'no-store, max-age=0');
      return new Response(patched, { status: upstream.status, statusText: upstream.statusText, headers: responseHeaders });
    }

    return new Response(upstream.body, { status: upstream.status, statusText: upstream.statusText, headers: responseHeaders });
  } catch (error) {
    console.error('İbrahim AI proxy failed', error);
    return new Response('İbrahim AI geçici olarak bağlanamadı.', { status: 502, headers: { 'content-type': 'text/plain; charset=utf-8' } });
  }
};

export const config: Config = { path: '/*' };
