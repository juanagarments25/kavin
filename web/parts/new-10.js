/* =========================================================
   10a. REWARDS — XP is granted only by genuine, once-only events.
   Every grant queues its System Message FIRST, then adds XP, so the
   order on screen is: reward → level up → rank promotion → milestone.
   ========================================================= */
const REMINDER_XP = 5, DAILY_CLEAR_BONUS = 20, STREAK_REWARD = 100;
const ACHIEVEMENTS = [
  { id:"awakening",   name:"The Awakening",       desc:"Clear your first Main or Side quest",            xp:20, test:(s)=>s.quests>=1 },
  { id:"first-clear", name:"First Clear",         desc:"Clear the daily quest for the first time",       xp:20, test:(s)=>s.clears>=1 },
  { id:"taskmaster",  name:"Taskmaster",          desc:"Complete 10 reminders",                          xp:30, test:(s)=>s.reminders>=10 },
  { id:"vanguard",    name:"Vanguard of Will",    desc:"Clear 5 Main quests",                            xp:50, test:(s)=>s.mains>=5 },
  { id:"discipline3", name:"Unbroken Discipline", desc:"Clear the daily quest 3 days in a row",          xp:30, test:(s,st)=>st>=3 },
  { id:"discipline7", name:"Shadow of Resolve",   desc:"Clear the daily quest 7 days in a row",          xp:70, test:(s,st)=>st>=7 }
];
const MILESTONES = [
  { id:"xp1000",  xp:1000,  title:"Awakened Hunter" },
  { id:"xp5000",  xp:5000,  title:"Shadow Vanguard" },
  { id:"xp10000", xp:10000, title:"Knight of the Abyss" },
  { id:"xp25000", xp:25000, title:"Shadow Commander" },
  { id:"xp50000", xp:50000, title:"Heir of the Monarch" }
];
const currentTitle = () => { let t = ""; for(const m of MILESTONES) if(state.player.miles[m.id]) t = m.title; return t; };
const awardsOn = () => state.profile.cats.system;
function grant(msg, xp){
  if(awardsOn()) SysMsg.push(msg);
  if(xp > 0) addXp(xp);
}
function checkAchievements(){
  const p = state.player, streak = currentStreak();
  for(const a of ACHIEVEMENTS){
    if(p.ach[a.id] || !a.test(p.stats, streak)) continue;
    p.ach[a.id] = Date.now(); persist("player");
    if(awardsOn()) logNotif("system", `Achievement unlocked: ${a.name}`, `${a.desc} · +${a.xp} XP`);
    grant({ id:`ach:${a.id}`, type:"achievement", kicker:"SYSTEM MESSAGE", title:"ACHIEVEMENT UNLOCKED", name:a.name, detail:a.desc,
            sub:"REWARD ACQUIRED", reward:`+${a.xp} XP`, voice:`Achievement unlocked. ${a.name}.` }, a.xp);
  }
}
function checkMilestones(silent){
  const p = state.player;
  for(const m of MILESTONES){
    if(p.miles[m.id] || p.xp < m.xp) continue;
    p.miles[m.id] = Date.now(); persist("player");
    if(silent) continue;
    if(awardsOn()){
      logNotif("system", `Milestone achieved: ${m.xp.toLocaleString()} XP`, `Title acquired: ${m.title}`);
      SysMsg.push({ id:`mile:${m.id}`, type:"milestone", kicker:"SYSTEM MESSAGE", title:"MILESTONE ACHIEVED", name:`${m.xp.toLocaleString()} TOTAL EXPERIENCE`,
        sub:"REWARD ACQUIRED", reward:`TITLE: ${m.title.toUpperCase()}`, voice:`Milestone achieved. Title acquired: ${m.title}.` });
    }
  }
  renderPlayer();
}
function addXp(n){
  if(!(n > 0)) return;
  const beforeLvl = levelInfo(state.player.xp).lvl;
  state.player.xp += n;
  const afterLvl = levelInfo(state.player.xp).lvl;
  const shown = state.player.lastLevel ?? beforeLvl;
  let ev = null;
  if(afterLvl > beforeLvl && afterLvl > shown){
    ev = { from: Math.max(beforeLvl, shown), to: afterLvl, gain: n, total: state.player.xp };
    state.player.lastLevel = afterLvl;          // saved with the XP: a refresh can never replay it
  } else if(state.player.lastLevel == null) state.player.lastLevel = afterLvl;
  persist("player"); renderPlayer();
  if(ev) levelUp(ev);
  checkMilestones(false);
}
function syncLastLevel(){ // first run / imports: treat the current level and passed milestones as already seen
  const lvl = levelInfo(state.player.xp).lvl;
  if(state.player.lastLevel !== lvl){ state.player.lastLevel = lvl; persist("player"); }
  checkMilestones(true);
}
function levelUp(ev){
  const rankBefore = levelInfo(xpForLevel(ev.from)).rank, {rank} = levelInfo(state.player.xp);
  logNotif("system", `Level up: level ${ev.to}`, `+${ev.gain} XP · total ${ev.total} XP`);
  SysMsg.push({ id:`lvl:${ev.to}`, type:"level", kicker:"SYSTEM MESSAGE", title:"LEVEL UP", levels:{from:ev.from, to:ev.to},
    sub:"YOUR POWER HAS INCREASED", gain:ev.gain, total:ev.total, voice:`Level up. Level ${ev.to} reached. Your power has increased.` });
  if(rank !== rankBefore){
    logNotif("system", `Rank promotion: ${rank}-Rank`, `Previous rank: ${rankBefore}-Rank`);
    SysMsg.push({ id:`rank:${rank}`, type:"rank", kicker:"SYSTEM MESSAGE", title:"RANK PROMOTION", ranks:{from:`${rankBefore}-RANK`, to:`${rank}-RANK`},
      level: ev.to, voice:`Rank promotion. You are now ${rank} rank.` });
  }
}

/* =========================================================
   10b. SHADOW SYSTEM MESSAGE — ONE reusable panel for every message.
   Queue is saved per device (survives refresh, never duplicates).
   ========================================================= */
const SysMsg = (() => {
  const dlg = $("#lvDlg"), stage = $("#lvStage"), canvas = $("#lvEmbers"), ctx = canvas.getContext("2d");
  const THEME_NAME = { blue:"Blue Shadow Energy", purple:"Royal Purple Flame", red:"Crimson Energy", gold:"Black-Gold Monarch", monarch:"Shadow Monarch Aura" };
  const ALERT_TYPES = new Set(["reminder","daily-pre","daily-due","warning"]);
  const reduceMQ = matchMedia("(prefers-reduced-motion: reduce)");
  let Q = { items:[], seen:[], paused:false }, storeKey = null;
  let playing = false, current = null, endNow = null, wakers = [], raf = null, embers = [];

  /* ---- persistence of the pending queue (per device, per account) ---- */
  const save = () => { if(storeKey) LS.set(storeKey, { items:Q.items.slice(0,40), seen:Q.seen.slice(-300), paused:Q.paused }); renderResume(); };
  function restore(accountId){
    storeKey = `jarvis.shadow.sysq.v1:${accountId || "local"}`;
    const v = LS.get(storeKey, null);
    Q = { items: Array.isArray(v?.items) ? v.items.filter(m=>m && m.id && m.type) : [], seen: Array.isArray(v?.seen) ? v.seen : [], paused: !!v?.paused };
    Q.items = Q.items.filter(stillValid);
    save();
    if(Q.items.length && !Q.paused) setTimeout(()=>run(), 1400);
  }
  function stillValid(m){ // drop queued alerts whose subject no longer needs attention
    if(m.type==="reminder"){ const r = state.reminders.find(x=>x.id===m.ref); return !!r && !r.done; }
    if(m.type==="daily-pre" || m.type==="daily-due") return m.day === state.daily.day && remainingGoals().length > 0;
    return true;
  }
  function push(m){
    if(!m || !m.id) return;
    m.at = m.at || Date.now();
    if(!m.preview){
      if(Q.seen.includes(m.id) || Q.items.some(x=>x.id===m.id) || current?.id===m.id) return;
      Q.items.push(m); save();
    } else Q.items.unshift(m);
    // a closed (paused) queue still lets real alerts through: a due reminder must not wait behind "Resume"
    if(ALERT_TYPES.has(m.type) && !m.preview){ Q.paused = false; save(); }
    if(!playing && (!Q.paused || m.preview)) run(); else renderResume();
  }

  /* ---- flames (built once, reused) ---- */
  function fill(row, n, big){
    row.textContent = "";
    for(let i=0;i<n;i++){
      const t = document.createElement("i");
      t.style.setProperty("--t", (0.7 + Math.random()*0.6).toFixed(2) + "s");
      t.style.setProperty("--dl", (-Math.random()*1.2).toFixed(2) + "s");
      t.style.setProperty("--sk", ((Math.random()*10)-5).toFixed(1) + "deg");
      if(big) t.style.height = (70 + Math.random()*30) + "%";
      row.append(t);
    }
  }
  fill($(".lv-f-top", dlg), 14); fill($(".lv-f-bottom", dlg), 14); fill($(".lv-core", dlg), 7, true);

  /* ---- timing helpers (cancellable) ---- */
  const wait = ms => new Promise(res => { if(endNow) return res(); const id = setTimeout(done, ms); function done(){ clearTimeout(id); wakers = wakers.filter(w=>w!==done); res(); } wakers.push(done); });
  const forever = () => new Promise(res => { if(endNow) return res(); wakers.push(res); });
  const nextFrame = () => new Promise(r => requestAnimationFrame(()=>r()));
  function end(how){ if(!current) return; endNow = how; wakers.splice(0).forEach(w=>w()); }

  /* ---- look ---- */
  function applyTheme(theme, s, m){
    dlg.classList.remove(...LV_THEMES.map(t=>"t-"+t));
    dlg.classList.add("t-"+theme);
    const low = s.intensity==="low" || reduceMQ.matches;
    dlg.classList.toggle("low", low);
    dlg.classList.toggle("noflames", !s.flames);
    dlg.classList.toggle("calm", m.type !== "level");
    dlg.classList.toggle("is-warning", m.type==="warning" || m.type==="daily-due" || (m.type==="reminder" && m.missed));
    dlg.style.setProperty("--spd", s.spd);
  }
  function show(id, on){ $(id).hidden = !on; }
  function fillText(m){
    const p2 = n => String(n).padStart(2,"0");
    $("#smKicker").textContent = m.kicker || "SYSTEM MESSAGE";
    $("#smTitle").textContent = m.title || "";
    show("#smLevels", !!m.levels);
    if(m.levels){ $("#lvFrom").textContent = `LEVEL ${p2(m.levels.from)}`; $("#lvTo").textContent = `LEVEL ${p2(m.levels.to)}`; }
    show("#smName", !!m.name); $("#smName").textContent = m.name || "";
    show("#smRanks", !!m.ranks); if(m.ranks){ $("#smRankFrom").textContent = m.ranks.from; $("#smRankTo").textContent = m.ranks.to; }
    show("#smSub", !!m.sub); $("#smSub").textContent = m.sub || "";
    show("#smReward", !!m.reward); $("#smReward").textContent = m.reward || "";
    show("#smDetail", !!m.detail); $("#smDetail").textContent = m.detail || "";
    const withStats = m.type === "level";
    show("#smStats", withStats); show("#lvBarWrap", withStats || ["quest","task","objective","daily","achievement","special"].includes(m.type));
    const info = levelInfo(m.total ?? state.player.xp);
    if(withStats){ $("#lvGain").textContent = `+${m.gain}`; $("#lvTotal").textContent = Number(m.total).toLocaleString(); $("#lvNext").textContent = `${info.into} / ${info.need}`; }
    const pct = Math.round(info.into/info.need*100); $("#lvBar").style.width = pct + "%"; $("#lvBarWrap").setAttribute("aria-valuenow", pct);
    const meta = $("#smMeta"); meta.textContent = "";
    for(const [k,v] of (m.meta||[])) meta.append(el("dt",{text:k}), el("dd",{text:v}));
    show("#smMeta", !!(m.meta && m.meta.length));
    $("#lvPreviewTag").hidden = !m.preview;
    // actions for alert messages (computed from type: queue items stay plain data)
    const acts = $("#smActs"); acts.textContent = "";
    if(m.type==="reminder" && !m.preview){
      acts.append(el("button",{class:"qi-btn main", type:"button", onclick:()=>act("open")}, "OPEN QUEST"),
                  el("button",{class:"qi-btn", type:"button", onclick:()=>act("done")}, "MARK COMPLETE"),
                  el("button",{class:"qi-btn", type:"button", onclick:()=>act("snooze")}, "SNOOZE 10 MIN"));
    } else if((m.type==="daily-pre" || m.type==="daily-due" || m.type==="warning") && !m.preview){
      acts.append(el("button",{class:"qi-btn main", type:"button", onclick:()=>act("quests")}, "OPEN QUEST"));
    }
    show("#smActs", !!acts.children.length);
  }
  function act(what){
    const m = current; if(!m) return;
    if(what==="done"){ end("next"); setDone(m.ref, true); }
    else if(what==="snooze"){ const r = state.reminders.find(x=>x.id===m.ref); if(r){ r.due = Date.now() + 10*60000; r.alerted = false; persist("reminders"); toast(`Snoozed until ${fmtTime(r.due)}.`); renderAll(); } end("next"); }
    else if(what==="open"){ end("close"); openFromNotif({id:m.nid, kind:"reminder", ref:m.ref}); }
    else if(what==="quests"){ end("close"); setTab("quests"); }
  }

  /* ---- sound: restrained, stopped on close ---- */
  function sound(m, transform, s){
    if(ALERT_TYPES.has(m.type)){ if(m.type==="warning" || m.type==="daily-due" || m.missed) { if(s.sound) tone([392, 294, 196], .16, .7, .08*(s.vol/100)); } else chime(); return; }
    if(!s.sound || !s.vol) return;
    const v = 0.13 * (s.vol/100);
    if(transform) tone([98, 147, 196, 294, 392, 587], .1, 1.1, v);
    else if(m.type==="level") tone([196, 262, 330, 392, 523], .1/s.spd, .7, v);
    else if(m.type==="rank") tone([147, 220, 294, 440], .12, .8, v);
    else tone([220, 330, 440], .09, .55, v*.8);
  }

  /* ---- energy particles (canvas), off in Low / reduced motion ---- */
  function startEmbers(s, m){
    stopEmbers();
    if(s.intensity==="low" || reduceMQ.matches) return;
    const r = canvas.getBoundingClientRect(), dpr = Math.min(devicePixelRatio||1, 2);
    canvas.width = r.width*dpr; canvas.height = r.height*dpr; ctx.setTransform(dpr,0,0,dpr,0,0);
    const cs = getComputedStyle(dlg);
    const cols = dlg.classList.contains("t-monarch") ? ["#3fe0ff","#9b5cff","#6b3cff","#e8f6ff"] : [cs.getPropertyValue("--fa").trim() || cs.getPropertyValue("--a").trim(), cs.getPropertyValue("--c").trim()];
    const n = m.type==="level" ? (s.intensity==="high" ? 48 : 24) : (s.intensity==="high" ? 22 : 12), W = r.width, H = r.height;
    embers = Array.from({length:n}, () => ({ x: Math.random()*W, y: H*0.4 + Math.random()*H*0.6, r: Math.random()*1.8+.5, vy: -(Math.random()*1+.3)*s.spd, vx:(Math.random()-.5)*.4, a: Math.random()*.5+.35, c: cols[Math.floor(Math.random()*cols.length)] }));
    let last = 0;
    const loop = t => {
      if(t - last > 33){ last = t; ctx.clearRect(0,0,W,H);
        for(const p of embers){
          p.x += p.vx; p.y += p.vy; p.a -= .006;
          if(p.y < -6 || p.a <= 0){ p.y = H - Math.random()*40; p.x = Math.random()*W; p.a = Math.random()*.5+.35; }
          ctx.globalAlpha = Math.max(0, p.a); ctx.fillStyle = p.c; ctx.shadowColor = p.c; ctx.shadowBlur = 10;
          ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI*2); ctx.fill();
        }
        ctx.globalAlpha = 1; }
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
  }
  function stopEmbers(){ if(raf){ cancelAnimationFrame(raf); raf = null; } ctx.clearRect(0,0,canvas.width,canvas.height); }

  async function appear(m, transform, fromTheme, toTheme, s, intense){
    stage.classList.remove("lv-show","lv-hide"); void stage.offsetWidth;
    dlg.classList.remove("transforming","intense");
    if(transform) applyTheme(fromTheme, s, m);
    stage.classList.add("lv-show");
    await nextFrame(); startEmbers(s, m);
    if(transform){
      await wait(550 / s.spd); if(endNow) return;
      applyTheme(toTheme, s, m); void dlg.offsetWidth; dlg.classList.add("transforming"); dlg.classList.toggle("intense", !!intense);
      sound(m, true, s); startEmbers(s, m);
    } else sound(m, false, s);
  }
  async function disappear(s){ stage.classList.remove("lv-show"); stage.classList.add("lv-hide"); await wait(330 / s.spd); stopEmbers(); }

  function finish(m){ // message handled: never shown again on this device
    if(m.preview) return;
    Q.items = Q.items.filter(x=>x.id!==m.id);
    if(!Q.seen.includes(m.id)) Q.seen.push(m.id);
    if(m.nid) markRead(m.nid);
    save();
  }
  async function run(){
    if(playing) return;
    playing = true;
    try{
      while(true){
        const m = Q.items.find(x=>x.preview) || Q.items.find(x=>!x.preview);
        if(!m || (Q.paused && !m.preview)) break;
        if(m.preview) Q.items = Q.items.filter(x=>x!==m);
        if(!stillValid(m)){ finish(m); continue; }
        current = m; endNow = null;
        const s = state.profile.lvl, isLevel = m.type==="level", sticky = ALERT_TYPES.has(m.type) && !m.preview;
        const reps = isLevel && s.repeat ? s.reps : 1;
        const lvlNow = m.levels ? m.levels.to : (m.level || levelInfo(state.player.xp).lvl);
        const tFrom = m.levels ? themeFor(m.levels.from, s) : themeFor(lvlNow, s), tTo = themeFor(lvlNow, s);
        const transform = isLevel && tFrom !== tTo;
        applyTheme(tTo, s, m); fillText(m);
        try{ if(!dlg.open) dlg.showModal(); }catch{ dlg.setAttribute("open",""); }
        $("#smClose").focus({preventScroll:true});
        if(sticky){ sendNotification(m.notifTitle || `${m.kicker} · ${m.title}`, m.notifBody || m.name || "", m.id); startTitleFlash(); }
        if(m.voice) setTimeout(()=>{ if(current===m && !endNow) speak(m.voice); }, ALERT_TYPES.has(m.type) && state.profile.chime ? 650 : 250);
        for(let i=0; i<reps && !endNow; i++){
          $("#lvCount").textContent = reps>1 ? `${i+1} / ${reps}` : "";
          const left = Q.items.filter(x=>x!==m && !x.preview).length;
          $("#smQueue").textContent = left ? `${left} MORE` : "";
          $("#lvSkip").hidden = sticky;
          await appear(m, transform && i===0, tFrom, tTo, s, transform);
          if(sticky) await forever(); else await wait((isLevel ? s.dur : s.msgDur) * 1000);
          if(endNow) break;
          await disappear(s);
          if(i < reps-1) await wait(s.delay * 1000);
        }
        // stop every sound and effect of this message
        stopAllSound(); stopEmbers(); stopTitleFlash();
        stage.classList.remove("lv-show","lv-hide"); dlg.classList.remove("transforming","intense");
        const how = endNow; current = null; endNow = null;
        finish(m);
        if(how === "close"){ if(Q.items.some(x=>!x.preview)){ Q.paused = true; save(); } break; }
      }
    } finally {
      if(dlg.open) dlg.close();
      playing = false; current = null; renderResume();
    }
  }
  function renderResume(){
    const b = $("#smResume"); if(!b) return;
    const n = Q.items.filter(x=>!x.preview).length;
    b.hidden = !(entered && Q.paused && n && !playing);
    b.textContent = `RESUME · ${n} SYSTEM MESSAGE${n>1?"S":""}`;
  }
  $("#lvSkip").addEventListener("click", ()=>end("next"));
  $("#smClose").addEventListener("click", ()=>end("close"));
  dlg.addEventListener("cancel", e => { e.preventDefault(); end("close"); });
  $("#smResume").addEventListener("click", ()=>{ Q.paused = false; save(); run(); });
  return { push, restore, run, get playing(){ return playing; }, get pending(){ return Q.items.length; }, themeName: t => THEME_NAME[t], renderResume,
           _q: () => Q };
})();
