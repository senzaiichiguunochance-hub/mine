'use strict';
const H=80,API='https://ja.wiktionary.org/w/api.php',MAX_LOG=100,ROUND_N=10,CHAR_OPTIONS=[1,2,3,4,5,6,7];
const BASE='あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん';
const DAKU='がぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽ',SMALL='ゃゅょっぁぃぅぇぉ',FREQ='あいうえかきくこさしすたとなにのはまもらりるれん';
const ids=['charCount','spinSpeed','script','mode','optDaku','optSmall','optEasy','startBtn','allStopBtn','resultWord','statusMessage','meaning','linkContainer','logTbody','clearLogBtn','csvBtn','onlyWin','soundBtn','vol','themeBtn','reelContainer','scoreBar','shareModal','shareText','shareX','shareLine','shareCopy','shareClose'];
const E={};ids.forEach(i=>E[i]=document.getElementById(i));
const ls={get(k,d){try{const v=localStorage.getItem(k);return v==null?d:JSON.parse(v)}catch{return d}},set(k,v){try{localStorage.setItem(k,JSON.stringify(v))}catch{}}};
const SEL=['charCount','spinSpeed','script','mode'],CHK=['optDaku','optSmall','optEasy'];
const NG1='をんゃゅょっぁぃぅぇぉヲンャュョッァィゥェォ';
let strip=[],reels=[],st='idle',last=0,rounds=0,rWins=0,cache=new Map();
let score=ls.get('slot_score',{tries:0,wins:0,streak:0,best:0}),logs=ls.get('slot_log',[]);

/* ---- 設定 ---- */
CHAR_OPTIONS.forEach(n=>E.charCount.add(new Option(n+'文字',n)));E.charCount.value='3';
const saved=ls.get('slot_set',{});
[...SEL,...CHK].forEach(i=>{const el=E[i];if(!(i in saved))return;if(el.type==='checkbox')el.checked=!!saved[i];else if([...el.options].some(o=>o.value===String(saved[i])))el.value=saved[i]});
function saveSet(){const o={};[...SEL,...CHK].forEach(i=>o[i]=E[i].type==='checkbox'?E[i].checked:E[i].value);ls.set('slot_set',o)}

/* ---- 音 ---- */
let ac=null,muted=ls.get('slot_mute',false),vol=ls.get('slot_vol',0.6);
E.vol.value=vol;E.soundBtn.textContent=muted?'🔇':'🔊';
function initAudio(){if(!ac){const C=window.AudioContext||window.webkitAudioContext;if(!C)return;ac=new C()}if(ac.state==='suspended')ac.resume()}
function tone(f,t,d,type,v){const o=ac.createOscillator(),g=ac.createGain();o.type=type;o.frequency.setValueAtTime(f,t);g.gain.setValueAtTime(v*vol,t);g.gain.linearRampToValueAtTime(.001,t+d);o.connect(g);g.connect(ac.destination);o.start(t);o.stop(t+d)}
function play(k){if(!ac||muted)return;try{const n=ac.currentTime;
if(k==='start'){tone(220,n,.15,'triangle',.2);tone(880,n+.07,.1,'triangle',.15)}
else if(k==='stop')tone(330,n,.08,'square',.15);
else[523.25,659.25,783.99,1046.5].forEach((f,i)=>tone(f,n+i*.1,.2,'sine',.2))}catch{}}
E.soundBtn.onclick=()=>{muted=!muted;ls.set('slot_mute',muted);E.soundBtn.textContent=muted?'🔇':'🔊'};
E.vol.oninput=()=>{vol=+E.vol.value;ls.set('slot_vol',vol)};

/* ---- テーマ ---- */
function setTheme(t){document.documentElement.dataset.theme=t;E.themeBtn.textContent=t==='dark'?'☀️':'🌙'}
setTheme(ls.get('slot_theme',matchMedia('(prefers-color-scheme:dark)').matches?'dark':'light'));
E.themeBtn.onclick=()=>{const t=document.documentElement.dataset.theme==='dark'?'light':'dark';ls.set('slot_theme',t);setTheme(t)};

/* ---- リール ---- */
function buildStrip(){let s=BASE;if(E.optDaku.checked)s+=DAKU;if(E.optSmall.checked)s+=SMALL;let a=[...s];
if(E.optEasy.checked){const f=[...FREQ];a=[...a,...f,...f]}
if(E.script.value==='k')a=a.map(c=>String.fromCharCode(c.charCodeAt(0)+0x60));return a}
function draw(r){r.inner.style.transform=`translateY(${-(r.pos%(r.strip.length*H))}px)`}
function createReels(){strip=buildStrip();E.reelContainer.textContent='';reels=[];const n=+E.charCount.value;
for(let i=0;i<n;i++){
  const s=i===0?strip.filter(c=>!NG1.includes(c)):strip,list=[...s,...s];
  const col=document.createElement('div');col.className='reel-column';
  const win=document.createElement('button');win.type='button';win.className='reel-window';win.setAttribute('aria-label',`${i+1}文字目のリール（押すとロック切替）`);
  const inner=document.createElement('div');inner.className='reel-inner';
  list.forEach(c=>{const d=document.createElement('div');d.className='reel-char';d.textContent=c;inner.append(d)});
  win.append(inner);
  const sb=document.createElement('button');sb.type='button';sb.className='stop-btn';sb.textContent='STOP';sb.disabled=true;sb.setAttribute('aria-label',`${i+1}文字目を止める`);
  const r={inner,win,sb,strip:s,pos:0,state:'idle',held:false,ch:s[0],from:0,to:0,t0:0};
  sb.onclick=()=>stopReel(i);
  win.onclick=()=>{if(st!=='idle')return;r.held=!r.held;win.classList.toggle('held',r.held)};
  col.append(win,sb);E.reelContainer.append(col);reels.push(r);draw(r)}
E.resultWord.textContent='--';E.meaning.textContent='';E.linkContainer.textContent='';setState('idle')}

/* ---- 状態管理 ---- */
function setState(s){st=s;const idle=s==='idle';E.startBtn.disabled=!idle;E.allStopBtn.disabled=s!=='spin';
[...SEL,...CHK].forEach(i=>E[i].disabled=!idle);reels.forEach(r=>r.win.disabled=!idle)}
function status(t,c){E.statusMessage.className='status-message '+(c||'');E.statusMessage.textContent=t}
function updateWord(){E.resultWord.textContent=reels.map(r=>r.state==='idle'?r.ch:'？').join('')}
function renderScore(){const s=score,rate=s.tries?Math.round(s.wins/s.tries*100):0;
E.scoreBar.textContent=`成功 ${s.wins}/${s.tries}（${rate}%）｜連続 ${s.streak}｜最高連続 ${s.best}`+(E.mode.value==='10'?`｜${rounds}/${ROUND_N}回`:'')}

/* ---- スロット動作（時間ベースで環境差なし） ---- */
function startSlot(){
  if(st!=='idle')return;
  if(!reels.length||reels.every(r=>r.held)){status('すべてロック中です。どれかを外してね','fail');return}
  initAudio();play('start');setState('spin');
  E.resultWord.classList.remove('success-anim');E.meaning.textContent='';E.linkContainer.textContent='';
  status('STOPで止めよう！（キー：1〜／Space）');
  reels.forEach(r=>{if(r.held)return;r.pos=Math.floor(Math.random()*r.strip.length)*H;r.state='spin';r.sb.disabled=false;r.win.classList.add('spin')});
  updateWord();last=performance.now();requestAnimationFrame(tick)}
function tick(now){const dt=Math.min(now-last,50),sp=+E.spinSpeed.value;last=now;let act=false;
  reels.forEach(r=>{
    if(r.state==='spin'){r.pos=(r.pos+sp*dt/1000)%(r.strip.length*H);draw(r);act=true}
    else if(r.state==='stopping'){const t=Math.min((now-r.t0)/260,1);r.pos=r.from+(r.to-r.from)*(1-(1-t)**3);draw(r);if(t<1)act=true;else finishReel(r)}});
  if(act)requestAnimationFrame(tick)}
function stopReel(i){const r=reels[i];if(!r||r.state!=='spin')return;
  r.state='stopping';r.sb.disabled=true;r.from=r.pos;r.to=Math.ceil(r.pos/H)*H;r.t0=performance.now();play('stop')}
function finishReel(r){r.pos=r.to%(r.strip.length*H);r.ch=r.strip[r.pos/H];r.state='idle';r.win.classList.remove('spin');
  r.win.classList.add('bounce');setTimeout(()=>r.win.classList.remove('bounce'),300);draw(r);updateWord();
  if(reels.every(x=>x.state==='idle'))onAllStopped()}
const stopAll=()=>reels.forEach((_,i)=>stopReel(i));

/* ---- 判定（3値：yes / no / err、キャッシュ・タイムアウト付き） ---- */
async function judge(w){
  if(cache.has(w))return cache.get(w);
  const c=new AbortController(),t=setTimeout(()=>c.abort(),6000);
  try{
    const r=await fetch(`${API}?action=query&prop=extracts|categories&exintro&explaintext&exlimit=1&cllimit=max&redirects=1&format=json&origin=*&titles=${encodeURIComponent(w)}`,{signal:c.signal});
    if(!r.ok)throw new Error(r.status);
    const p=Object.values((await r.json()).query.pages)[0];let res;
    if(p.missing!==undefined)res={s:'no'};
    else{const cats=(p.categories||[]).map(x=>x.title);
      res=cats.length&&!cats.some(x=>x.includes('日本語'))?{s:'no',note:'日本語以外の項目'}:{s:'yes',mean:meaning(p.extract)}}
    cache.set(w,res);return res;
  }catch(e){console.error(e);return{s:'err'}}finally{clearTimeout(t)}}
function meaning(x){return(x||'').split('\n').map(l=>l.trim()).filter(l=>l&&!/^=/.test(l)).slice(0,2).join(' / ').slice(0,90)}

async function onAllStopped(){
  setState('judge');const w=reels.map(r=>r.ch).join('');E.resultWord.textContent=w;status('Wiktionaryで判定中...');
  const res=await judge(w),url=`https://ja.wiktionary.org/wiki/${encodeURIComponent(w)}`;
  lastRes={w,s:res.s};
  E.linkContainer.textContent='';E.meaning.textContent='';
  if(res.s==='yes'){play('win');status(`🎉 「${w}」は実在する言葉です！`,'success');E.resultWord.classList.add('success-anim');confetti();E.meaning.textContent=res.mean?'📝 '+res.mean:''}
  else if(res.s==='no')status(`❌ 「${w}」は辞書に見つかりませんでした${res.note?`（${res.note}）`:''}`,'fail');
  else status('⚠ 通信エラーで判定できませんでした（記録しません）','fail');
  const a=document.createElement('a');a.className='dict-link';a.href=url;a.target='_blank';a.rel='noopener noreferrer';a.textContent=`📖 Wiktionaryで「${w}」を確認 ↗`;const sh=document.createElement('button');sh.type='button';sh.className='dict-link share-open';sh.textContent='📣 SNSに投稿';sh.onclick=openShare;E.linkContainer.append(a,sh);
  if(res.s!=='err'){
    const ok=res.s==='yes';score.tries++;if(ok){score.wins++;score.streak++;score.best=Math.max(score.best,score.streak)}else score.streak=0;ls.set('slot_score',score);
    logs.unshift({w,ok});logs.splice(MAX_LOG);ls.set('slot_log',logs);renderLog();
    if(w.length>=2&&w.length<=5){const b=document.createElement('button');b.type='button';b.className='ana';b.textContent='🔀 並び替えを調べる';b.onclick=()=>anagram(w,b);E.linkContainer.append(b)}
    if(E.mode.value==='10'){rounds++;if(ok)rWins++;if(rounds>=ROUND_N){status(`🏁 ${ROUND_N}回終了！ ${rWins}/${ROUND_N} 成功`,'success');rounds=0;rWins=0}}
  }
  renderScore();setState('idle')}

/* ---- 並び替え（アナグラム） ---- */
function perms(s){const out=new Set();(function p(a,b){if(!b.length){out.add(a);return}for(let i=0;i<b.length;i++)p(a+b[i],b.slice(0,i)+b.slice(i+1))})('',s);out.delete(s);return[...out]}
async function anagram(w,btn){
  btn.disabled=true;btn.textContent='調査中...';const ps=perms(w),found=[];let msg;
  try{for(let i=0;i<ps.length;i+=50){
      const r=await fetch(`${API}?action=query&format=json&origin=*&titles=${encodeURIComponent(ps.slice(i,i+50).join('|'))}`);
      if(!r.ok)throw 0;Object.values((await r.json()).query.pages).forEach(p=>{if(p.missing===undefined)found.push(p.title)})}
    msg=found.length?`🔀 並び替えで存在: ${found.join('、')}`:'🔀 並び替えで存在する言葉はありません'}
  catch{msg='⚠ 並び替えの調査に失敗しました'}
  const s=document.createElement('span');s.textContent=msg;s.style.fontSize='13px';btn.replaceWith(s)}

/* ---- ログ ---- */
function renderLog(){E.logTbody.textContent='';
  logs.filter(l=>!E.onlyWin.checked||l.ok).forEach(l=>{
    const tr=document.createElement('tr'),td=(t,c)=>{const d=document.createElement('td');d.textContent=t;if(c)d.className=c;tr.append(d);return d};
    td(l.w).style.fontWeight='bold';td(l.ok?'⭕ 存在する':'❌ なし',l.ok?'ok':'ng');
    const x=document.createElement('a');x.href='https://ja.wiktionary.org/wiki/'+encodeURIComponent(l.w);x.target='_blank';x.rel='noopener noreferrer';x.textContent='リンク';td('').append(x);
    E.logTbody.append(tr)})}
E.onlyWin.onchange=renderLog;
E.clearLogBtn.onclick=()=>{logs=[];ls.set('slot_log',logs);renderLog()};
E.csvBtn.onclick=async()=>{
  const csv='word,exists,url\n'+logs.map(l=>`${l.w},${l.ok},https://ja.wiktionary.org/wiki/${encodeURIComponent(l.w)}`).join('\n');
  try{await navigator.clipboard.writeText(csv);E.csvBtn.textContent='コピーしました';}catch{E.csvBtn.textContent='コピー不可'}
  setTimeout(()=>E.csvBtn.textContent='CSVコピー',1500)};

/* ---- 演出 ---- */
function confetti(){if(matchMedia('(prefers-reduced-motion:reduce)').matches)return;
  for(let i=0;i<36;i++){const s=document.createElement('span');s.className='cf';s.textContent=['🎉','✨','⭐','🎊'][i%4];
    s.style.left=Math.random()*100+'vw';s.style.animationDelay=Math.random()*.5+'s';document.body.append(s);setTimeout(()=>s.remove(),2600)}}

/* ---- 入力 ---- */
E.startBtn.onclick=startSlot;E.allStopBtn.onclick=stopAll;
[...SEL.filter(i=>i!=='spinSpeed'),...CHK].forEach(i=>E[i].onchange=()=>{saveSet();rounds=0;rWins=0;createReels();renderScore()});
E.spinSpeed.onchange=saveSet;
addEventListener('keydown',e=>{
  if(/^(SELECT|INPUT|TEXTAREA)$/.test(e.target.tagName))return;
  if(e.code==='Space'){if(e.target.tagName==='BUTTON')return;e.preventDefault();if(st==='idle')startSlot();else if(st==='spin')stopAll()}
  else if(/^[1-7]$/.test(e.key)&&st==='spin')stopReel(+e.key-1)});

/* ---- SNS投稿（子画面） ---- */
let lastRes=null;
function buildShareText(){
  const s=score,rate=s.tries?Math.round(s.wins/s.tries*100):0;
  const ok=[...new Set(logs.filter(l=>l.ok).map(l=>l.w))].slice(0,6),L=['🎰 50音スロット言葉合わせで遊んだよ！'];
  if(lastRes)L.push(`「${lastRes.w}」が揃った！`+(lastRes.s==='yes'?'⭕ 実在する言葉！':lastRes.s==='no'?'❌ 惜しい…':''));
  if(ok.length)L.push(`揃った言葉：${ok.join('、')}`);
  L.push(`成功 ${s.wins}/${s.tries}（${rate}%）｜最高連続 ${s.best}`,'#50音スロット');
  return L.join('\n')+'\n'}
function openShare(){E.shareText.value=buildShareText();E.shareModal.showModal()}
E.shareClose.onclick=()=>E.shareModal.close();
E.shareModal.addEventListener('click',e=>{if(e.target===E.shareModal)E.shareModal.close()});
E.shareX.onclick=()=>window.open(`https://twitter.com/intent/tweet?text=${encodeURIComponent(E.shareText.value)}&url=${encodeURIComponent(location.href)}`,'_blank','noopener');
E.shareLine.onclick=()=>window.open(`https://social-plugins.line.me/lineit/share?text=${encodeURIComponent(E.shareText.value+'\n'+location.href)}`,'_blank','noopener');
E.shareCopy.onclick=async()=>{
  try{await navigator.clipboard.writeText(E.shareText.value+'\n'+location.href);E.shareCopy.textContent='コピーしました'}catch{E.shareCopy.textContent='コピー不可'}
  setTimeout(()=>E.shareCopy.textContent='コピー',1500)};

/* ---- アクセス解析（常時） ---- */
(function(){const s=document.createElement('script');s.async=true;s.dataset.goatcounter='https://senzaiichiguunochance.goatcounter.com/count';s.src='//gc.zgo.at/count.js';document.head.append(s)})();

/* ---- 初期化 ---- */
createReels();renderLog();renderScore();
if('serviceWorker'in navigator&&location.protocol.startsWith('http'))navigator.serviceWorker.register('sw.js').catch(()=>{});
