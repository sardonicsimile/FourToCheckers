'use strict';
const http=require('node:http'),crypto=require('node:crypto');
const html=require('node:fs').readFileSync(require('node:path').join(__dirname,'index.html'));
const test60=require('node:fs').readFileSync(require('node:path').join(__dirname,'test60.html'));
const rooms=new Map(), limits=new Map();
const rand=()=>crypto.randomBytes(32).toString('base64url');
const fail=(res,status,message)=>send(res,status,{error:message});
const send=(res,status,obj)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(obj))};
function throttle(req,key,max){
 const now=Date.now(),addr=req.socket.remoteAddress||'',id=addr+':'+key,v=limits.get(id)||{since:now,count:0};
 if(now-v.since>60000){v.since=now;v.count=0}v.count++;limits.set(id,v);return v.count>max;
}
async function body(req,max=100000){
 let size=0,chunks=[];for await(const part of req){size+=part.length;if(size>max)throw Object.assign(Error('Too large'),{status:413});chunks.push(part)}
 return Buffer.concat(chunks);
}
async function json(req){try{return JSON.parse((await body(req)).toString()||'{}')}catch{throw Object.assign(Error('Invalid JSON'),{status:400})}}
function role(req,room){
 const value=(req.headers.authorization||'').replace(/^Bearer /,'');
 if(value.length<32)return null;
 for(const r of ['viewer','sender'])if(room[r]&&value.length===room[r].length&&crypto.timingSafeEqual(Buffer.from(value),Buffer.from(room[r])))return r;
 return null;
}
function ice(){
 const a=[{urls:'stun:stun.l.google.com:19302'}];
 if(process.env.TURN_URLS&&process.env.TURN_SECRET){
 const u=(Math.floor(Date.now()/1000)+3600)+':'+rand().slice(0,10);
 a.push({urls:process.env.TURN_URLS.split(',').map(x=>x.trim()),username:u,credential:crypto.createHmac('sha1',process.env.TURN_SECRET).update(u).digest('base64')});
 }return a;
}
setInterval(()=>{const now=Date.now();for(const [k,v] of rooms)if(v.expire<now)rooms.delete(k);for(const [k,v] of limits)if(now-v.since>60000)limits.delete(k)},30000).unref();
http.createServer(async(req,res)=>{
 try{
 res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('X-Frame-Options','DENY');
 res.setHeader('Referrer-Policy','no-referrer');res.setHeader('Permissions-Policy','camera=(), microphone=(), geolocation=()');
 const url=new URL(req.url,'http://localhost'),p=url.pathname;
 if(req.method==='GET'&&(p==='/'||p==='/board'||p==='/test'||p==='/test60')){res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'});res.end(p==='/test60'?test60:html);return}
 if(req.method==='GET'&&p==='/apk'){
  res.writeHead(302,{'Location':'https://github.com/sardonicsimile/FourToCheckers/releases/download/tablink-board-test-v0.7/app-debug.apk','Cache-Control':'no-store'});res.end();return;
 }
 if(req.method==='GET'&&p==='/install'){
  res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'});
  res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>TabLink Board APK</title><style>body{background:#f7f7f8;color:#202124;font:20px Arial,sans-serif;max-width:760px;margin:50px auto;padding:20px}h1{font-size:42px}p{line-height:1.5;color:#56606a}.btn{display:block;background:#17191f;color:white;text-decoration:none;text-align:center;border-radius:16px;padding:23px;font-weight:bold;margin:35px 0}.card{background:white;padding:28px;border:1px solid #e5e5e5;border-radius:20px}</style></head><body><div class="card"><h1>TabLink Board v0.7</h1><p>Free experimental receiver app for Android 8+ Boxlight displays.</p><a href="/apk" class="btn">Download Android test app</a><p><b>Already installed v0.6?</b> Uninstall the old TabLink Board app first. This test build uses a new temporary Android signing key and cannot update the previous APK directly.</p><p>Install only if allowed by your school. This app receives video; it does not yet enable native iPhone background broadcasting. Video frame rate is not guaranteed. Use the website for now if installation fails.</p><p><a href="/">Back to TabLink</a></p></div></body></html>');return;
 }
 if(req.method==='GET'&&p==='/healthz')return send(res,200,{ok:true,rooms:rooms.size});
 if(req.method==='POST'&&p==='/api/create'){
  if(throttle(req,'create',12))return fail(res,429,'Too many requests');
  if(rooms.size>250)return fail(res,503,'Server busy');
  let code;do{code=String(crypto.randomInt(1000000)).padStart(6,'0')}while(rooms.has(code));
  const r={viewer:rand(),sender:null,expire:Date.now()+600000,viewerQueue:[],senderQueue:[],frame:null,frameId:0,ended:false};
  rooms.set(code,r);return send(res,200,{code,token:r.viewer,expire:r.expire,ice:ice()});
 }
 if(req.method==='POST'&&p==='/api/join'){
  if(throttle(req,'join',12))return fail(res,429,'Too many code attempts');
  const b=await json(req),c=String(b.code||''),r=/^\d{6}$/.test(c)?rooms.get(c):null;
  if(!r||r.sender||r.expire<Date.now()||r.ended)return fail(res,404,'Code expired or unavailable');
  r.sender=rand();r.expire=Date.now()+4*3600000;r.viewerQueue.push({type:'joined'});
  return send(res,200,{code:c,token:r.sender,ice:ice()});
 }
 const match=/^\/api\/(poll|signal|frame|end)\/(\d{6})$/.exec(p);
 if(!match)return fail(res,404,'Not found');
 const action=match[1],c=match[2],r=rooms.get(c);
 if(!r||r.expire<Date.now())return fail(res,404,'Session expired');
 const who=role(req,r);if(!who)return fail(res,403,'Unauthorized');
 const own=r[who+'Queue'],other=r[(who==='viewer'?'sender':'viewer')+'Queue'];
 if(action==='poll'&&req.method==='GET')return send(res,200,{messages:own.splice(0,100),ended:r.ended});
 if(r.ended)return fail(res,410,'Session ended');
 if(action==='end'&&req.method==='POST'){r.ended=true;r.frame=null;r.expire=Date.now()+30000;other.push({type:'ended'});return send(res,200,{ok:true})}
 if(action==='signal'&&req.method==='POST'){
  const b=await json(req);
  if(!['offer','answer','ice'].includes(b.type))return fail(res,400,'Invalid signal');
  if(['offer','answer'].includes(b.type)&&(!['string'].includes(typeof b.sdp)||b.sdp.length>90000))return fail(res,400,'Invalid SDP');
  if(b.type==='ice'&&(!b.candidate||typeof b.candidate.candidate!=='string'))return fail(res,400,'Invalid ICE');
  if(other.length>=150)return fail(res,429,'Signal queue full');
  other.push(b);return send(res,200,{ok:true});
 }
 if(action==='frame'&&req.method==='POST'&&who==='sender'){
  const b=await body(req,420000);if(b.length<3||b[0]!==255||b[1]!==216||b[2]!==255)return fail(res,400,'JPEG required');
  r.frame=b;r.frameId++;return send(res,200,{ok:true});
 }
 if(action==='frame'&&req.method==='GET'&&who==='viewer'){
  if(!r.frame||url.searchParams.get('after')===String(r.frameId)){res.writeHead(204);res.end();return}
  res.writeHead(200,{'Content-Type':'image/jpeg','Cache-Control':'no-store','X-Frame-Id':String(r.frameId)});res.end(r.frame);return;
 }
 return fail(res,405,'Method not allowed');
 }catch(e){fail(res,e.status||500,e.status?e.message:'Server error')}
}).listen(Number(process.env.PORT||8080),'0.0.0.0',()=>console.log('TabLink listening'));
