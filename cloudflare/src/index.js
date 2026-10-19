const headers = {"Cache-Control":"no-store","X-Content-Type-Options":"nosniff","Referrer-Policy":"no-referrer"};
const text = (message,status=200)=>new Response(message,{status,headers});
const signalHeaders={"Content-Type":"application/json",...headers};
const validId = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const html = id => `<!doctype html>
<html lang="pt"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LiveStream · Ver em direto</title>
<style>
*{box-sizing:border-box}html,body{margin:0;height:100%;font-family:system-ui,Segoe UI,sans-serif;background:#0b1020;color:#fff}
.page{height:100%;display:flex;flex-direction:column;gap:10px;padding:12px}
header{display:flex;align-items:center;justify-content:space-between;gap:12px}
.brand{font-size:18px;font-weight:750;letter-spacing:-.4px}.brand span{color:#4e9aff}
.actions{display:flex;gap:8px;align-items:center}
button{background:#1877f2;border:0;color:white;padding:11px 16px;border-radius:11px;font-weight:700;cursor:pointer}
button:hover{background:#0d65d8}button:focus-visible{outline:3px solid #a3c7ff}
#stage{background:#000;border:1px solid #27334b;border-radius:14px;min-height:0;flex:1;display:flex;align-items:center;justify-content:center;overflow:hidden;position:relative}
#video{width:100%;height:100%;object-fit:contain;display:none}
#placeholder{color:#bac5d8;text-align:center;padding:20px}
footer{color:#b7c1d2;font-size:13px;display:flex;align-items:center;justify-content:space-between;gap:10px}
#stage:fullscreen{border:0;border-radius:0;width:100vw;height:100vh}
#stage:fullscreen #video{width:100%;height:100%;object-fit:contain}
#stage:fullscreen #placeholder{font-size:20px}
@media(max-width:520px){header{flex-wrap:wrap}.brand{font-size:16px}button{padding:10px 12px}footer{font-size:11px}}
</style></head>
<body><main class="page"><header><div class="brand"><span>●</span> LiveStream <small style="font-weight:400;color:#aebbd2">· Em direto</small></div><div class="actions"><button id="sound" type="button" aria-label="Ativar som">🔇 Ativar som</button><button id="fullscreen" type="button" aria-label="Ver em ecrã inteiro">⛶ Ecrã inteiro</button></div></header>
<section id="stage"><img id="video" alt="Ecrã partilhado em direto"><div id="placeholder">A aguardar transmissão…</div></section>
<footer><span id="state" role="status">A ligar…</span><span>Som opcional · ESC para sair do ecrã inteiro</span></footer></main>
<script>
const id=${JSON.stringify(id)};
let etag='',currentUrl='',running=false;
const img=document.getElementById('video'),state=document.getElementById('state'),placeholder=document.getElementById('placeholder'),stage=document.getElementById('stage'),button=document.getElementById('fullscreen');
button.addEventListener('click',async()=>{
 try{if(document.fullscreenElement)await document.exitFullscreen();else await stage.requestFullscreen()}
 catch{state.textContent='O navegador não permitiu o ecrã inteiro'}
});
document.addEventListener('fullscreenchange',()=>{button.textContent=document.fullscreenElement?'⤢ Sair do ecrã inteiro':'⛶ Ecrã inteiro'});
async function poll(){
 try{
  const response=await fetch('/api/frame/'+id,{cache:'no-store',headers:etag?{'If-None-Match':etag}:{}});
  if(response.status===304){state.textContent='● Em direto'}
  else if(response.ok){
   etag=response.headers.get('ETag')||'';
   const url=URL.createObjectURL(await response.blob());
   img.onload=()=>{if(currentUrl)URL.revokeObjectURL(currentUrl);currentUrl=url;img.style.display='block';placeholder.style.display='none'};
   img.src=url;state.textContent='● Em direto · imagem recebida';
  }else{state.textContent='A aguardar imagens…';etag=''}
 }catch{state.textContent='Ligação interrompida · a tentar novamente'}
}
async function loop(){if(!document.hidden&&!running){running=true;try{await poll()}finally{running=false}}setTimeout(loop,document.hidden?2000:220)}
let audioContext=null,audioTag='',nextAudioTime=0,audioActive=false;
const soundButton=document.getElementById('sound');
soundButton.addEventListener('click',async()=>{
 if(audioActive){audioActive=false;soundButton.textContent='🔇 Ativar som';return}
 try{
  audioContext=audioContext||new (window.AudioContext||window.webkitAudioContext)();
  await audioContext.resume();nextAudioTime=audioContext.currentTime+.12;
  audioActive=true;soundButton.textContent='🔊 Desativar som';
 }catch{state.textContent='O navegador bloqueou a reprodução do áudio'}
});
async function pollAudio(){
 if(audioActive && audioContext && !document.hidden){
  try{
   const response=await fetch('/api/audio/'+id,{cache:'no-store',headers:audioTag?{'If-None-Match':audioTag}:{}});
   if(response.ok){
    audioTag=response.headers.get('ETag')||'';
    const bytes=await response.arrayBuffer();
    const samples=new Int16Array(bytes);
    const buffer=audioContext.createBuffer(1,samples.length,16000);
    const channel=buffer.getChannelData(0);
    for(let i=0;i<samples.length;i++)channel[i]=samples[i]/32768;
    const source=audioContext.createBufferSource();source.buffer=buffer;source.connect(audioContext.destination);
    const now=audioContext.currentTime;
    if(nextAudioTime<now || nextAudioTime>now+.9)nextAudioTime=now+.08;
    source.start(nextAudioTime);nextAudioTime+=buffer.duration;
   }else if(response.status===404){audioTag=''}
  }catch{}
 }
 setTimeout(pollAudio,140);
}
loop();pollAudio();
</script></body></html>`;
export default {
 async fetch(request,env){
  const url=new URL(request.url);
  if(url.pathname==="/health")return new Response(JSON.stringify({ok:true}),{headers:{"Content-Type":"application/json",...headers}});
  const match=url.pathname.match(/^\/(api\/frame|api\/signal|api\/audio|watch)\/([0-9a-f-]{36})$/);
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
 constructor(ctx,env){this.ctx=ctx;this.key=null;this.frame=null;this.updated=0;this.sequence=0;this.signals=[];this.audio=null;this.audioSeq=0;this.audioUpdated=0}
 async fetch(request){
  const now=Date.now();
  if(new URL(request.url).pathname.includes("/api/audio/")){
   if(request.method==="POST"){
    const key=request.headers.get("X-Publish-Key")||"";
    if(!this.key || key!==this.key)return text("Forbidden",403);
    if(request.headers.get("Content-Type")!=="application/octet-stream")return text("Invalid audio",415);
    const data=await request.arrayBuffer();
    if(data.byteLength<256 || data.byteLength>16384 || data.byteLength%2!==0)return text("Invalid audio chunk",413);
    this.audio=data;this.audioUpdated=now;this.audioSeq++;
    return new Response(null,{status:204,headers});
   }
   if(request.method==="GET"){
    if(!this.audio || now-this.audioUpdated>2500)return text("No audio",404);
    const tag='"'+this.audioSeq+'"';
    if(request.headers.get("If-None-Match")===tag)return new Response(null,{status:304,headers:{"ETag":tag,...headers}});
    return new Response(this.audio,{headers:{"Content-Type":"application/octet-stream","ETag":tag,...headers}});
   }
   return text("Method not allowed",405);
  }
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