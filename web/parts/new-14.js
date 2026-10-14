let titleFlash = null, lastCheck = 0;
function checkAll(){
  if(!entered) return;
  lastCheck = Date.now();
  if(rolloverDaily()) renderDaily();
  let changed = false;
  const stamp = t => `${fmtTime(t)} · ${new Date(t).toLocaleDateString([], {day:"numeric", month:"short"})}`;
  for(const r of state.reminders){
    if(!r.done && !r.alerted && r.due <= Date.now()){
      r.alerted = true; changed = true; // mark first, so it can never alert twice
      const missed = Date.now() - r.due > 2*60000;
      const nid = logNotif("reminder", r.title, r.notes || (missed ? `Was due ${fmtTime(r.due)}` : `Due ${fmtTime(r.due)}`), r.id);
      if(state.profile.cats.reminder){
        const name = displayName();
        SysMsg.push(missed
          ? { id:`rem:${r.id}:${r.due}`, type:"reminder", missed:true, ref:r.id, nid, kicker:"SYSTEM WARNING", title:"QUEST INCOMPLETE", name:r.title,
              sub:"OBJECTIVE REMAINS UNRESOLVED", detail:r.notes, meta:[["WAS DUE", stamp(r.due)], ["NOW", stamp(Date.now())]],
              voice:`${name ? name + ". " : ""}Warning. ${r.title}. Objective remains unresolved.`, notifTitle:"SYSTEM WARNING · Quest incomplete", notifBody:r.title }
          : { id:`rem:${r.id}:${r.due}`, type:"reminder", ref:r.id, nid, kicker:"SYSTEM MESSAGE", title:"QUEST ARRIVED", name:r.title,
              sub:"PERSONAL REMINDER", detail:r.notes, meta:[["TIME", stamp(Date.now())], ["DUE", stamp(r.due)]],
              voice:`${name ? name + ". " : ""}Reminder. ${r.title}${r.notes ? ". " + r.notes : ""}`, notifTitle:"SYSTEM MESSAGE · Personal reminder", notifBody:r.title + (r.notes ? "\n" + r.notes : "") });
      }
    }
  }
  if(changed) persist("reminders");
  const d = state.daily, today = dayKey(), dl = deadlineTs(), left = remainingGoals();
  if(d.goals.length && left.length){
    const lines = left.map(g=>`${g.title}  ${fmtCount(g)}`).join("\n");
    if(d.remindBefore>0 && d.alertedPre!==today && Date.now() >= dl - d.remindBefore*60000 && Date.now() < dl){
      d.alertedPre = today; persist("daily");
      const nid = logNotif("daily", "Daily Quest ends soon", `${left.length} objective${left.length>1?"s":""} left before ${fmtTime(dl)}.`);
      if(state.profile.cats.daily) SysMsg.push({ id:`dp:${today}`, type:"daily-pre", day:today, nid, kicker:"SYSTEM MESSAGE", title:"DAILY QUEST",
        name:`DEADLINE ${fmtTime(dl)}`, sub:`${left.length} OBJECTIVE${left.length>1?"S":""} REMAIN`, detail:lines, meta:[["TIME", stamp(Date.now())]],
        voice:`Daily quest. ${left.length} objective${left.length>1?"s":""} remain before ${fmtTime(dl)}.`, notifTitle:"SYSTEM MESSAGE · Daily Quest", notifBody:`${left.length} objectives remain · deadline ${fmtTime(dl)}` });
    }
    if(d.alertedDue!==today && Date.now() >= dl){
      d.alertedDue = today; d.alertedPre = today; persist("daily");
      const nid = logNotif("daily", "Daily Quest incomplete", `${left.length} objective${left.length>1?"s":""} unresolved. No penalty.`);
      if(state.profile.cats.daily) SysMsg.push({ id:`dd:${today}`, type:"daily-due", day:today, nid, kicker:"SYSTEM WARNING", title:"QUEST INCOMPLETE",
        name:"DAILY QUEST", sub:"OBJECTIVE REMAINS UNRESOLVED", detail:`${lines}\n\nThe deadline has passed. No penalty is applied.`, meta:[["DEADLINE", fmtTime(dl)]],
        voice:"Warning. Daily quest incomplete. Objective remains unresolved.", notifTitle:"SYSTEM WARNING · Daily Quest", notifBody:`${left.length} objectives unresolved` });
    }
  }
  renderReminders(); renderNext(); renderWatch();
  if(d.goals.length && left.length && Date.now() >= dl && !$("#dailyWarn").classList.contains("overdue")) renderDaily();
}
const SAMPLES = {
  level:      () => { const L = Math.max(2, levelInfo(state.player.xp).lvl + 1); return { type:"level", kicker:"SYSTEM MESSAGE", title:"LEVEL UP", levels:{from:L-1,to:L}, sub:"YOUR POWER HAS INCREASED", gain:xpToNext(L-1), total:xpForLevel(L)+10, level:L }; },
  quest:      () => ({ type:"quest", kicker:"SYSTEM MESSAGE", title:"QUEST COMPLETED", name:"Defeat the Exam Boss", sub:"REWARD ACQUIRED", reward:"+50 XP" }),
  achievement:() => ({ type:"achievement", kicker:"SYSTEM MESSAGE", title:"ACHIEVEMENT UNLOCKED", name:"Unbroken Discipline", detail:"Clear the daily quest 3 days in a row", sub:"REWARD ACQUIRED", reward:"+30 XP" }),
  daily:      () => ({ type:"daily", kicker:"SYSTEM MESSAGE", title:"DAILY QUEST CLEARED", name:"ALL OBJECTIVES COMPLETE", sub:"BONUS REWARD ACQUIRED", reward:`+${DAILY_CLEAR_BONUS} XP` }),
  rank:       () => ({ type:"rank", kicker:"SYSTEM MESSAGE", title:"RANK PROMOTION", ranks:{from:"D-RANK", to:"C-RANK"}, level:10 }),
  milestone:  () => ({ type:"milestone", kicker:"SYSTEM MESSAGE", title:"MILESTONE ACHIEVED", name:"1,000 TOTAL EXPERIENCE", sub:"REWARD ACQUIRED", reward:"TITLE: AWAKENED HUNTER" }),
  task:       () => ({ type:"task", kicker:"SYSTEM MESSAGE", title:"TASK COMPLETED", name:"Submit project report", sub:"EXPERIENCE ACQUIRED", reward:`+${REMINDER_XP} XP` }),
  special:    () => ({ type:"special", kicker:"SHADOW SYSTEM REWARD", title:"SHADOW SYSTEM REWARD", name:"Seven days of unbroken discipline", sub:"REWARD ACQUIRED", reward:`+${STREAK_REWARD} XP` }),
  warning:    () => ({ type:"warning", kicker:"SYSTEM WARNING", title:"QUEST INCOMPLETE", name:"DAILY QUEST", sub:"OBJECTIVE REMAINS UNRESOLVED", detail:"Study session  [20/60 min]\nDrink water  [5/8 glasses]" }),
  reminder:   () => ({ type:"reminder", kicker:"SYSTEM MESSAGE", title:"QUEST ARRIVED", name:"Submit project report", sub:"PERSONAL REMINDER", meta:[["TIME", fmtTime(Date.now())]] })
};
function previewMsg(kind, level){
  const m = (SAMPLES[kind] || SAMPLES.quest)();
  if(level && kind==="level"){ m.levels = {from:level-1, to:level}; m.gain = xpToNext(level-1); m.total = xpForLevel(level)+Math.round(xpToNext(level)*.12); m.level = level; }
  else if(level) m.level = level;
  SysMsg.push({ ...m, id:`preview:${uid()}`, preview:true });
}
$("#btnPreview").addEventListener("click", ()=> previewMsg("reminder"));
