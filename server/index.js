import http from 'node:http';
import {randomUUID} from 'node:crypto';
const sessions=new Map();
const port=Number(process.env.PORT||3000);
const maxBytes=3*1024*1024;
const page=(id)=>`<!doctype html><html lang="pt"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LiveStream</title><style>body{margin:0;background:#090e1b;color:white;font-family:system-ui;text-align:center}header{padding:18px}img{max-width:100vw;max-height:85vh;object-fit:contain}</style><header><h2>LiveStream · Em direto</h2><p>Imagem do ecrã (sem áudio nesta versão)</p></header><img id="frame" alt="A aguardar transmissão"><script>const id=${JSON.stringify(id)};setInterval(()=>{document.getElementById('frame').src='/api/frame/'+id+'?t='+Date.now()},700)</script></html>`;
const server=http.createServer(async(req,res)=>{
 const path=new URL(req.url,'http://localhost').pathname;
 res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
 if(path==='/health'){res.writeHead(200,{'Content-Type':'application/json'});return res.end(JSON.stringify({ok:true}));}
 const match=path.match(/^\/api\/frame\/([a-f0-9-]{36})$/);
 if(match){
  const id=match[1];
  if(req.method==='POST'){
   // MVP only: possession of session id grants upload permission. Add authenticated publishing before public production use.
   const parts=[];let size=0;
   for await(const chunk of req){size+=chunk.length;if(size>maxBytes){res.writeHead(413);return res.end();}parts.push(chunk);}
   if(req.headers['content-type']!=='image/jpeg'){res.writeHead(415);return res.end();}
   sessions.set(id,{data:Buffer.concat(parts),at:Date.now()});
   res.writeHead(204);return res.end();
  }
  const frame=sessions.get(id);
  if(!frame||Date.now()-frame.at>10000){res.writeHead(404);return res.end('Sem imagem');}
  res.writeHead(200,{'Content-Type':'image/jpeg'});return res.end(frame.data);
 }
 const watch=path.match(/^\/watch\/([a-f0-9-]{36})$/);
 if(watch){res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Referrer-Policy':'no-referrer'});return res.end(page(watch[1]));}
 res.writeHead(404);res.end('Not found');
});
setInterval(()=>{for(const [id,v] of sessions)if(Date.now()-v.at>60000)sessions.delete(id)},60000).unref();
server.listen(port,'0.0.0.0',()=>console.log('LiveStream relay on',port));