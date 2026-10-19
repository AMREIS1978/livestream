const headers = {"Cache-Control":"no-store","X-Content-Type-Options":"nosniff","Referrer-Policy":"no-referrer"};
const text = (message,status=200)=>new Response(message,{status,headers});
const signalHeaders={"Content-Type":"application/json",...headers};
const validId = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const html = id => `<!doctype html><html lang="pt"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LiveStream</title><style>body{background:#0c1221;color:white;font-family:system-ui;text-align:center;margin:0;padding:20px}img{max-width:95vw;max-height:80vh;object-fit:contain;border-radius:12px}small{color:#b7c1d2}</style><h1>LiveStream</h1><p>Partilha do ecrã em direto</p><img id="video" alt="Transmissão" style="display:none"><p id="state"><small>A aguardar transmissão…</small></p><script>const id=${JSON.stringify(id)};let etag='';const img=document.getElementById('video');const state=document.getElementById('state');async function poll(){try{const r=await fetch('/api/frame/'+id,{cache:'no-store',headers:etag?{'If-None-Match':etag}:{}});if(r.status===304){state.textContent='● EM DIRETO · a aguardar nova imagem'}else if(r.ok){etag=r.headers.get('ETag')||'';const blob=await r.blob();const url=URL.createObjectURL(blob);const old=img.dataset.url;img.src=url;img.style.display='block';img.dataset.url=url;if(old)setTimeout(()=>URL.revokeObjectURL(old),1500);state.textContent='● EM DIRETO · imagem (sem áudio)'}else{state.textContent='A aguardar imagens do telemóvel…'}}catch{state.textContent='Ligação interrompida. A tentar novamente…'}}let running=false;async function loop(){if(!document.hidden&&!running){running=true;await poll();running=false}setTimeout(loop,document.hidden?2000:220)}document.addEventListener('visibilitychange',()=>{if(!document.hidden)state.textContent='A retomar transmissão…'});loop()</script></html>`;
export default {
 async fetch(request,env){
  const url=new URL(request.url);
  if(url.pathname==="/health")return new Response(JSON.stringify({ok:true}),{headers:{"Content-Type":"application/json",...headers}});
  const match=url.pathname.match(/^\/(api\/frame|api\/signal|watch)\/([0-9a-f-]{36})$/);
  if(!match || !validId.test(match[2]))return text("Not found",404);
  const [,kind,id]=match;
  if(kind==="watch"){
   if(request.method!=="GET")return text("Method not allowed",405);
   return new Response(html(id),{headers:{"Content-Type":"text/html; charset=utf-8",...headers}});
  }
  const room=env.STREAMS.get(env.STREAMS.idFromName(id));
  return room.fetch(request);
 }
};
export class StreamRoom {
 constructor(ctx,env){this.ctx=ctx;this.key=null;this.frame=null;this.updated=0;this.sequence=0;this.signals=[]}
 async fetch(request){
  const now=Date.now();
  if(new URL(request.url).pathname.includes("/api/signal/")){
   if(request.method==="POST"){
    const key=request.headers.get("X-Publish-Key")||"";
    if(!this.key || key!==this.key)return text("Forbidden",403);
    const body=await request.text();
    if(body.length>16000)return text("Signal too large",413);
    let signal;try{signal=JSON.parse(body)}catch{return text("Invalid JSON",400)}
    if(!["offer","answer","candidate"].includes(signal.type))return text("Invalid signal",400);
    this.signals.push({signal,at:now});this.signals=this.signals.filter(x=>now-x.at<60000).slice(-32);
    return new Response(null,{status:204,headers});
   }
   if(request.method==="GET")return new Response(JSON.stringify({signals:this.signals.filter(x=>now-x.at<60000).map(x=>x.signal)}),{headers:signalHeaders});
   return text("Method not allowed",405);
  }
  if(request.method==="POST"){
   if(request.headers.get("Content-Type")!=="image/jpeg")return text("JPEG required",415);
   const key=request.headers.get("X-Publish-Key")||"";
   if(key.length<50 || key.length>160)return text("Invalid key",403);
   if(this.key!==null && this.key!==key)return text("Forbidden",403);
   const buffer=await request.arrayBuffer();
   if(buffer.byteLength<100 || buffer.byteLength>700000)return text("Invalid frame size",413);
   const signature=new Uint8Array(buffer,0,3);
   if(signature[0]!==255 || signature[1]!==216 || signature[2]!==255)return text("Invalid JPEG",415);
   this.key=key;this.frame=buffer;this.updated=now;this.sequence++;
   return new Response(null,{status:204,headers});
  }
  if(request.method==="GET"){
   if(!this.frame || now-this.updated>20000)return text("Offline",404);
   const etag=`"${this.sequence}"`;
   if(request.headers.get("If-None-Match")===etag)return new Response(null,{status:304,headers:{"ETag":etag,...headers}});
   return new Response(this.frame,{headers:{"Content-Type":"image/jpeg","ETag":etag,...headers}});
  }
  return text("Method not allowed",405);
 }
}