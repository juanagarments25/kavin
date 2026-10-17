/* =========================================================================
   LinkControl X — app logic (vanilla JS, no build step)
   Sections (search these headers to jump to a feature):
     1) HELPERS        6) ACTIVITY MONITOR (demo)
     2) NAVIGATION     7) SECURITY SCAN + CHECKLIST (real local checks)
     3) CLOCK + NET    8) PAIRING (QR generate + camera scan, local demo)
     4) BACKGROUND FX  9) SETTINGS
     5) DEVICE INFO   10) PWA + INIT
   Nothing here contacts a network or another device. All checks inspect THIS
   browser only; the "activity monitor" is clearly-labeled demo data.
   ========================================================================= */
(function () {
  "use strict";

  /* ---------------- 1) HELPERS ---------------- */
  const $ = (id) => document.getElementById(id);
  const qsa = (sel, root = document) => Array.from(root.querySelectorAll(sel));
  const pad = (n) => String(n).padStart(2, "0");

  const store = {
    get(k, d) { try { const v = localStorage.getItem("lcx_" + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem("lcx_" + k, JSON.stringify(v)); } catch (e) {} },
    del(k) { try { localStorage.removeItem("lcx_" + k); } catch (e) {} },
  };

  const settings = Object.assign(
    { theme: "violet", reduceMotion: false, particles: true, haptics: false },
    store.get("settings", {})
  );
  function saveSettings() { store.set("settings", settings); }

  function toast(msg, kind) {
    const t = document.createElement("div");
    t.className = "toast " + (kind || "");
    t.textContent = msg;
    $("toasts").appendChild(t);
    if (settings.haptics && navigator.vibrate) { try { navigator.vibrate(kind === "err" ? [40, 40, 40] : 25); } catch (e) {} }
    setTimeout(() => { t.style.opacity = "0"; t.style.transform = "translateY(-8px)"; setTimeout(() => t.remove(), 250); }, 2600);
  }

  const LOG_MAX = 100;
  function log(message, level) {
    const entry = { ts: Date.now(), message, level: level || "info" };
    const logs = store.get("logs", []);
    logs.unshift(entry);
    store.set("logs", logs.slice(0, LOG_MAX));
    if ($("view-logs").classList.contains("active")) renderLogs();
  }

  /* ---------------- 2) NAVIGATION ---------------- */
  let navStack = [];           // history of visited views, for the Back button
  let currentView = "dashboard";

  function showView(name, fromBack) {
    if (!fromBack && name !== currentView) navStack.push(currentView);
    currentView = name;
    qsa(".view").forEach((v) => v.classList.toggle("active", v.id === "view-" + name));
    qsa(".tab").forEach((t) => t.classList.toggle("active", t.dataset.view === name));
    // Back button shows whenever there is somewhere to go back to.
    $("backBtn").hidden = navStack.length === 0;
    if (name === "logs") renderLogs();
    window.scrollTo(0, 0);
  }
  function goBack() {
    if (!navStack.length) return;
    showView(navStack.pop(), true);
  }
  qsa("#tabbar .tab").forEach((t) => t.addEventListener("click", () => showView(t.dataset.view)));
  $("backBtn").addEventListener("click", goBack);
  // Android hardware/gesture back → step back inside the app instead of leaving it
  window.addEventListener("popstate", () => { if (navStack.length) { goBack(); history.pushState(null, ""); } });
  try { history.pushState(null, ""); } catch (e) {}

  // pairing sub-tabs
  $("pt-show").addEventListener("click", () => setPairTab("show"));
  $("pt-scan").addEventListener("click", () => setPairTab("scan"));
  function setPairTab(which) {
    $("pt-show").classList.toggle("active", which === "show");
    $("pt-scan").classList.toggle("active", which === "scan");
    $("pair-show").classList.toggle("hidden", which !== "show");
    $("pair-scan").classList.toggle("hidden", which !== "scan");
    if (which !== "scan") stopCamera();
  }

  /* ---------------- 3) CLOCK + NETWORK ---------------- */
  function tickClock() {
    const d = new Date();
    $("clock").textContent = pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
  }
  setInterval(tickClock, 1000); tickClock();

  function updateNet() {
    const on = navigator.onLine;
    $("netdot").classList.toggle("off", !on);
    $("netdot").title = on ? "Online" : "Offline";
  }
  window.addEventListener("online", () => { updateNet(); toast("Back online", "ok"); log("Network online", "ok"); });
  window.addEventListener("offline", () => { updateNet(); toast("You are offline", "warn"); log("Network offline", "warn"); });
  updateNet();

  /* ---------------- 4) BACKGROUND FX ---------------- */
  const fx = (function () {
    const cv = $("fx"); if (!cv) return { restart() {} };
    const ctx = cv.getContext("2d");
    let W = 0, H = 0, parts = [], blobs = [], raf = 0, nextBolt = 0;
    const off = () => settings.reduceMotion || !settings.particles ||
      window.matchMedia("(prefers-reduced-motion:reduce)").matches;
    function accentRGB() { return getComputedStyle(document.documentElement).getPropertyValue("--accent").trim() || "#8B5CF6"; }
    function size() {
      W = cv.width = window.innerWidth; H = cv.height = window.innerHeight;
      parts = Array.from({ length: 30 }, () => ({ x: Math.random() * W, y: Math.random() * H, r: Math.random() * 1.8 + 0.6, s: Math.random() * 0.4 + 0.15, o: Math.random() * 0.5 + 0.2, c: Math.random() > 0.5 ? "139,92,246" : "192,38,211" }));
      blobs = Array.from({ length: 4 }, (_, i) => ({ x: Math.random() * W, y: Math.random() * H, r: W * (0.35 + Math.random() * 0.3), dx: (Math.random() - 0.5) * 0.12, dy: (Math.random() - 0.5) * 0.12, c: i % 2 ? "139,92,246" : "56,189,248" }));
    }
    function paintStatic() { ctx.clearRect(0, 0, W, H); for (const p of parts) { ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, 6.3); ctx.fillStyle = "rgba(" + p.c + "," + p.o + ")"; ctx.fill(); } }
    function bolt() {
      ctx.save(); ctx.strokeStyle = "rgba(192,120,255,.8)"; ctx.lineWidth = 1.4; ctx.shadowBlur = 12; ctx.shadowColor = "rgba(139,92,246,.9)";
      let x = Math.random() * W, y = 0; ctx.beginPath(); ctx.moveTo(x, y);
      while (y < H) { y += 18 + Math.random() * 26; x += (Math.random() - 0.5) * 40; ctx.lineTo(x, y); }
      ctx.stroke(); ctx.restore();
      const el = $("lightning"); if (el) { el.classList.remove("flash"); void el.offsetWidth; el.classList.add("flash"); }
    }
    function frame(t) {
      ctx.clearRect(0, 0, W, H);
      for (const b of blobs) { b.x += b.dx; b.y += b.dy; if (b.x < -b.r) b.x = W + b.r; if (b.x > W + b.r) b.x = -b.r; if (b.y < -b.r) b.y = H + b.r; if (b.y > H + b.r) b.y = -b.r; const g = ctx.createRadialGradient(b.x, b.y, 0, b.x, b.y, b.r); g.addColorStop(0, "rgba(" + b.c + ",.06)"); g.addColorStop(1, "transparent"); ctx.fillStyle = g; ctx.fillRect(0, 0, W, H); }
      for (const p of parts) { p.y -= p.s; if (p.y < -4) { p.y = H + 4; p.x = Math.random() * W; } ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, 6.3); ctx.fillStyle = "rgba(" + p.c + "," + p.o + ")"; ctx.shadowBlur = 8; ctx.shadowColor = "rgba(" + p.c + ",.8)"; ctx.fill(); }
      ctx.shadowBlur = 0;
      if (t > nextBolt) { bolt(); nextBolt = t + 4500 + Math.random() * 5000; }
      raf = requestAnimationFrame(frame);
    }
    function restart() { if (raf) cancelAnimationFrame(raf), raf = 0; size(); if (off()) paintStatic(); else { nextBolt = performance.now() + 2500; raf = requestAnimationFrame(frame); } }
    window.addEventListener("resize", () => { size(); if (off()) paintStatic(); });
    restart();
    return { restart };
  })();

  /* ---------------- 5) DEVICE INFO (real) ---------------- */
  async function renderDeviceInfo() {
    const nav = navigator;
    const rows = [
      ["Platform", nav.platform || "—"],
      ["Browser engine", (/Chrome/.test(nav.userAgent) ? "Chromium" : /Firefox/.test(nav.userAgent) ? "Gecko" : /Safari/.test(nav.userAgent) ? "WebKit" : "—")],
      ["Screen", window.screen.width + " × " + window.screen.height + " @ " + (window.devicePixelRatio || 1) + "x"],
      ["Viewport", window.innerWidth + " × " + window.innerHeight],
      ["Language", nav.language || "—"],
      ["CPU cores", nav.hardwareConcurrency || "unknown"],
      ["Device memory", nav.deviceMemory ? nav.deviceMemory + " GB" : "unknown"],
      ["Connection", (nav.connection && nav.connection.effectiveType) ? nav.connection.effectiveType : "unknown"],
      ["Secure context", window.isSecureContext ? "yes (HTTPS/localhost)" : "no"],
      ["Online", nav.onLine ? "yes" : "no"],
    ];
    let html = rows.map(([k, v]) => '<div class="kv"><span>' + k + "</span><b>" + v + "</b></div>").join("");
    $("deviceInfo").innerHTML = html;
    // battery is async + optional
    if (nav.getBattery) {
      try {
        const b = await nav.getBattery();
        const row = '<div class="kv"><span>Battery</span><b>' + Math.round(b.level * 100) + "% " + (b.charging ? "⚡ charging" : "") + "</b></div>";
        $("deviceInfo").insertAdjacentHTML("beforeend", row);
      } catch (e) {}
    }
  }

  /* ---------------- 5b) LIVE MONITOR (real-time sensors) ---------------- */
  function startLiveMonitor() {
    // network (updates on change)
    function net() {
      const c = navigator.connection;
      $("lvNet").textContent = navigator.onLine
        ? (c && c.effectiveType ? c.effectiveType.toUpperCase() + (c.downlink ? " · " + c.downlink + "Mb" : "") : "online")
        : "offline";
    }
    net();
    window.addEventListener("online", net); window.addEventListener("offline", net);
    if (navigator.connection && navigator.connection.addEventListener)
      navigator.connection.addEventListener("change", net);

    // battery (live level/charging)
    if (navigator.getBattery) {
      navigator.getBattery().then((b) => {
        const show = () => { $("lvBatt").textContent = Math.round(b.level * 100) + "%" + (b.charging ? " ⚡" : ""); };
        show(); b.addEventListener("levelchange", show); b.addEventListener("chargingchange", show);
      }).catch(() => { $("lvBatt").textContent = "n/a"; });
    } else { $("lvBatt").textContent = "n/a"; }

    // screen orientation (live)
    function orient() {
      const o = (screen.orientation && screen.orientation.type) || (window.orientation != null ? window.orientation + "°" : "—");
      $("lvOrient").textContent = String(o).replace("-primary", "").replace("-secondary", " (2)");
    }
    orient();
    if (screen.orientation && screen.orientation.addEventListener) screen.orientation.addEventListener("change", orient);
    window.addEventListener("orientationchange", orient);

    $("lvMotion").textContent = "tap to enable";
  }

  function enableMotion() {
    const start = () => {
      window.addEventListener("devicemotion", (e) => {
        const a = e.accelerationIncludingGravity || e.acceleration;
        if (a) { const mag = Math.sqrt((a.x || 0) ** 2 + (a.y || 0) ** 2 + (a.z || 0) ** 2); $("lvMotion").textContent = mag.toFixed(1) + " m/s²"; }
      });
      $("lvNote").textContent = "Move or tilt the phone — the motion value updates live.";
      $("sensorBtn").hidden = true;
      log("Motion sensors enabled", "ok");
    };
    // iOS 13+ requires a permission request from a user gesture
    if (typeof DeviceMotionEvent !== "undefined" && typeof DeviceMotionEvent.requestPermission === "function") {
      DeviceMotionEvent.requestPermission().then((s) => { if (s === "granted") start(); else { toast("Motion permission denied", "warn"); } })
        .catch(() => toast("Motion sensor unavailable", "warn"));
    } else if (typeof DeviceMotionEvent !== "undefined") { start(); }
    else { $("lvMotion").textContent = "n/a"; toast("No motion sensor on this device", "warn"); }
  }
  $("sensorBtn").addEventListener("click", enableMotion);

  /* ---------------- 5c) LIVE TEST (real-time self-test) ----------------
     Exercises the real BroadcastChannel messaging path against an in-page peer
     and reports the actual, measured timings — a genuine real-time test. */
  function runLiveTest() {
    if (typeof BroadcastChannel === "undefined") { toast("Live link not supported in this browser", "warn"); return; }
    const term = $("ltTerm"), btn = $("ltRun");
    btn.disabled = true;
    $("ltStatus").textContent = "Running…"; $("ltHand").textContent = "—"; $("ltAvg").textContent = "—";
    term.innerHTML = "";
    const t0 = performance.now();
    const line = (cls, txt) => {
      const d = new Date();
      term.innerHTML += '<span class="t">[' + pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds()) + "] </span>" +
        '<span class="' + cls + '">' + txt + "</span>\n";
      term.scrollTop = term.scrollHeight;
    };

    const name = "lcx-selftest-" + Math.random().toString(36).slice(2, 7);
    const peer = new BroadcastChannel(name);   // simulated remote peer (in-page)
    const main = new BroadcastChannel(name);   // our side
    const rtts = [];
    let pings = 0, handshakeAt = 0;
    const PINGS = 5;
    const cleanup = () => { try { peer.close(); main.close(); } catch (e) {} };
    const fail = (why) => { line("w", "✕ " + why); $("ltStatus").textContent = "Failed"; btn.disabled = false; cleanup(); };
    const watchdog = setTimeout(() => fail("timed out (no response)"), 4000);

    // Peer: auto-accept + echo pings (this is the "other device")
    peer.onmessage = (e) => {
      const m = e.data; if (!m || m.side === "peer") return;
      if (m.t === "hello") peer.postMessage({ side: "peer", t: "welcome" });
      else if (m.t === "ping") peer.postMessage({ side: "peer", t: "pong", ts: m.ts });
    };

    // Our side
    main.onmessage = (e) => {
      const m = e.data; if (!m || m.side !== "peer") return;
      if (m.t === "welcome") {
        handshakeAt = performance.now() - t0;
        $("ltHand").textContent = handshakeAt.toFixed(1) + " ms";
        line("g", "✓ handshake ok (" + handshakeAt.toFixed(1) + " ms)");
        line("t", "sending " + PINGS + " pings…");
        sendPing();
      } else if (m.t === "pong") {
        const rtt = performance.now() - m.ts; rtts.push(rtt);
        line("v", "  ↳ pong " + rtts.length + "/" + PINGS + " · " + rtt.toFixed(1) + " ms");
        if (rtts.length >= PINGS) finish(); else setTimeout(sendPing, 180);
      }
    };
    function sendPing() { pings++; main.postMessage({ side: "me", t: "ping", ts: performance.now() }); }
    function finish() {
      clearTimeout(watchdog);
      const avg = rtts.reduce((a, b) => a + b, 0) / rtts.length;
      $("ltAvg").textContent = avg.toFixed(1) + " ms";
      $("ltStatus").textContent = "Passed";
      line("g", "✓ all " + PINGS + " round-trips ok · avg " + avg.toFixed(1) + " ms");
      // sensor availability (real capability check)
      line("t", "sensors: " +
        (navigator.getBattery ? "battery ✓ " : "battery – ") +
        (navigator.connection ? "network ✓ " : "network – ") +
        (typeof DeviceMotionEvent !== "undefined" ? "motion ✓" : "motion –"));
      toast("Live test passed · avg " + avg.toFixed(1) + " ms", "ok");
      log("Live test passed — handshake " + handshakeAt.toFixed(1) + "ms, avg RTT " + avg.toFixed(1) + "ms", "ok");
      btn.disabled = false; cleanup();
    }

    line("t", "opening live channel " + name);
    line("t", "handshake → hello");
    main.postMessage({ side: "me", t: "hello" });
  }
  $("ltRun").addEventListener("click", runLiveTest);

  /* ---------------- 6) ACTIVITY MONITOR (demo) ---------------- */
  const DEMO_EVENTS = [
    ["g", "handshake ok "], ["t", "peer kyra-7 "],
    ["t", "heartbeat 32ms "], ["v", "cursed-link stable"],
    ["w", "unknown probe "], ["t", "blocked at pairing port"],
    ["g", "token rotated "], ["t", "ttl 300s"],
    ["t", "signaling idle "], ["v", "awaiting pair"],
  ];
  let actTimer = 0;
  function startActivity() {
    const term = $("activity"); if (!term) return;
    term.innerHTML = '<span class="t">[demo feed — simulated, not real traffic]\n</span>';
    let i = 0;
    clearInterval(actTimer);
    const push = () => {
      const d = new Date();
      const stamp = '<span class="t">[' + pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds()) + "] </span>";
      const ev = DEMO_EVENTS[i % DEMO_EVENTS.length];
      term.innerHTML += stamp + '<span class="' + ev[0] + '">' + ev[1] + "</span>\n";
      term.scrollTop = term.scrollHeight;
      // keep it from growing forever
      if (term.childNodes.length > 60) term.removeChild(term.firstChild);
      i++;
    };
    push();
    actTimer = setInterval(() => { if (!settings.reduceMotion) push(); }, 2200);
  }

  /* ---------------- 7) SECURITY SCAN + CHECKLIST (real local checks) ---------------- */
  // Each check inspects THIS browser only and returns {status, detail}.
  const CHECKS = [
    { id: "secure", title: "Secure context (HTTPS)",
      run: () => window.isSecureContext
        ? { status: "pass", detail: "The page is a secure context, so camera, service workers and storage APIs are allowed." }
        : { status: "fail", detail: "Not a secure context. Serve the app over HTTPS (or localhost) — camera and PWA install need it." } },
    { id: "tls", title: "Transport",
      run: () => ({ status: location.protocol === "https:" ? "pass" : (location.protocol === "file:" ? "warn" : "warn"),
        detail: location.protocol === "https:" ? "Loaded over HTTPS." : "Loaded over " + location.protocol + " — fine for local testing, use HTTPS when hosting." }) },
    { id: "sw", title: "Service worker support",
      run: () => ("serviceWorker" in navigator)
        ? { status: "pass", detail: "Service workers are supported, so the app can work offline and be installed." }
        : { status: "warn", detail: "No service worker support in this browser — offline/install will be unavailable." } },
    { id: "storage", title: "Local storage",
      run: () => { try { localStorage.setItem("lcx_probe", "1"); localStorage.removeItem("lcx_probe"); return { status: "pass", detail: "Settings and logs can be saved on this device." }; } catch (e) { return { status: "warn", detail: "Storage is blocked (private mode?). Settings won't persist." }; } } },
    { id: "cam", title: "Camera permission", async: true,
      run: async () => {
        if (!navigator.permissions || !navigator.permissions.query) return { status: "warn", detail: "Permission status API unavailable; the browser will ask when you scan." };
        try { const p = await navigator.permissions.query({ name: "camera" });
          return { status: p.state === "denied" ? "warn" : "pass", detail: "Camera permission is '" + p.state + "'. It is only used to read a QR code, over HTTPS, and nothing is uploaded." };
        } catch (e) { return { status: "warn", detail: "Could not read camera permission; the browser will prompt on use." }; } } },
    { id: "notif", title: "Notifications",
      run: () => ("Notification" in window)
        ? { status: Notification.permission === "denied" ? "warn" : "pass", detail: "Notification permission is '" + Notification.permission + "'. Optional — alerts still show inside the app." }
        : { status: "warn", detail: "Notifications API not available." } },
    { id: "online", title: "Network reachability",
      run: () => navigator.onLine ? { status: "pass", detail: "The device reports an active network connection." } : { status: "warn", detail: "Offline. The app still runs; pairing a live peer would need a connection." } },
    { id: "cookies", title: "Cookies / state",
      run: () => navigator.cookieEnabled ? { status: "pass", detail: "Stateful features are allowed." } : { status: "warn", detail: "Cookies disabled — some state features may not work." } },
    { id: "dnt", title: "Tracking preference",
      run: () => { const dnt = navigator.doNotTrack === "1" || window.doNotTrack === "1"; return { status: dnt ? "pass" : "warn", detail: dnt ? "Do Not Track is on — good privacy posture." : "Do Not Track is off. This app does no tracking regardless." }; } },
    { id: "iso", title: "Origin isolation",
      run: () => ({ status: window.crossOriginIsolated ? "pass" : "warn", detail: window.crossOriginIsolated ? "Cross-origin isolated." : "Not cross-origin isolated — expected for a simple static site; no action needed." }) },
  ];

  let scanAbort = false, scanning = false;
  const CIRC = 264; // 2πr for r=42

  function renderChecklistPending() {
    $("checklist").innerHTML = CHECKS.map((c) =>
      '<div class="check pending" id="chk-' + c.id + '"><div class="mk">·</div><div><div class="t">' + c.title + '</div><div class="d">Not run yet.</div></div></div>'
    ).join("");
    $("scanCount").textContent = "0 / " + CHECKS.length;
  }

  function setCheckRow(c, res) {
    const el = $("chk-" + c.id); if (!el) return;
    el.className = "check " + res.status;
    const mark = res.status === "pass" ? "✓" : res.status === "warn" ? "!" : "✕";
    el.querySelector(".mk").textContent = mark;
    el.querySelector(".d").textContent = res.detail;
  }

  async function startScan() {
    if (scanning) return;
    scanning = true; scanAbort = false;
    $("startScanBtn").disabled = true; $("stopScanBtn").disabled = false;
    $("scanState").textContent = "SCAN";
    let pass = 0, warn = 0, fail = 0;
    $("cPass").textContent = "0"; $("cWarn").textContent = "0"; $("cFail").textContent = "0";
    renderChecklistPending();
    log("Security scan started", "info");

    for (let i = 0; i < CHECKS.length; i++) {
      if (scanAbort) break;
      const c = CHECKS[i];
      await sleep(settings.reduceMotion ? 10 : 260);
      let res;
      try { res = c.async ? await c.run() : c.run(); } catch (e) { res = { status: "warn", detail: "Check could not complete." }; }
      setCheckRow(c, res);
      if (res.status === "pass") pass++; else if (res.status === "warn") warn++; else fail++;
      $("cPass").textContent = pass; $("cWarn").textContent = warn; $("cFail").textContent = fail;
      const done = i + 1;
      $("scanCount").textContent = done + " / " + CHECKS.length;
      const p = done / CHECKS.length;
      $("scanArc").setAttribute("stroke-dashoffset", String(CIRC * (1 - p)));
      $("scanPct").textContent = Math.round(p * 100) + "%";
    }

    scanning = false;
    $("startScanBtn").disabled = false; $("stopScanBtn").disabled = true;
    if (scanAbort) {
      $("scanState").textContent = "STOPPED";
      toast("Scan stopped", "warn"); log("Security scan stopped by user", "warn");
      return;
    }
    $("scanState").textContent = "DONE";
    const score = Math.round(((pass + warn * 0.5) / CHECKS.length) * 100);
    updatePosture(score, pass, warn, fail);
    toast("Scan complete · posture " + score + "/100", fail ? "warn" : "ok");
    log("Security scan complete — posture " + score + "/100 (" + pass + " pass, " + warn + " warn, " + fail + " fail)", fail ? "warn" : "ok");
  }

  function stopScan() { if (scanning) scanAbort = true; }

  function updatePosture(score, pass, warn, fail) {
    store.set("posture", { score, pass, warn, fail, at: Date.now() });
    $("scoreVal").textContent = score;
    $("scoreArc").setAttribute("stroke-dashoffset", String(CIRC * (1 - score / 100)));
    const pill = $("scorePill");
    pill.className = "pill " + (score >= 80 ? "ok" : score >= 55 ? "warn" : "");
    pill.innerHTML = '<i class="dot"></i> ' + (score >= 80 ? "SECURE" : score >= 55 ? "REVIEW ADVISED" : "ATTENTION NEEDED");
    $("scoreNote").innerHTML = pass + " passed · " + warn + " warnings · " + fail + " failed. Tap <b>Scan</b> to re-run.";
  }

  function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

  $("startScanBtn").addEventListener("click", startScan);
  $("stopScanBtn").addEventListener("click", stopScan);

  /* ---------------- 8) PAIRING (real live link over BroadcastChannel) ----------------
     Two tabs/windows of this app (same browser) form a genuine real-time link:
     a host advertises a 6-digit code; a peer joins by that code; they exchange a
     live handshake + heartbeat with measured latency. QR generate/scan still work.
     Cross-DEVICE linking over the internet would need a signaling server (not here). */
  let pairCode = null, pairExpiry = 0, pairTimer = 0;
  const myId = Math.random().toString(36).slice(2, 8);
  let chan = null, role = null, hbTimer = 0, pingSent = 0, lastSeen = 0, aliveTimer = 0;

  function genPairing() {
    pairCode = String(Math.floor(100000 + Math.random() * 900000));
    pairExpiry = Date.now() + 5 * 60 * 1000;
    const payload = JSON.stringify({ app: "linkcontrol-x", v: 1, c: pairCode, exp: pairExpiry });
    try {
      const qr = qrcode(0, "M"); qr.addData(payload); qr.make();
      $("qrImg").src = qr.createDataURL(6, 8);
    } catch (e) { toast("QR library failed to load", "err"); }
    $("pairCode").textContent = pairCode.replace(/(\d{3})(\d{3})/, "$1 $2");
    $("pairState").innerHTML = '<i class="dot live"></i> READY TO PAIR';
    clearInterval(pairTimer);
    pairTimer = setInterval(updatePairCountdown, 1000);
    updatePairCountdown();
    openChannel(pairCode, "host");
    log("Pairing code generated · hosting live channel", "info");
  }

  // --- live link over BroadcastChannel ---
  function openChannel(code, asRole) {
    closeChannel();
    if (typeof BroadcastChannel === "undefined") { toast("Live link not supported in this browser", "warn"); return; }
    role = asRole;
    chan = new BroadcastChannel("lcx-pair-" + code);
    chan.onmessage = (e) => onChanMsg(e.data);
    if (asRole === "join") chan.postMessage({ t: "hello", from: myId });
  }
  function onChanMsg(m) {
    if (!m || m.from === myId) return;
    if (m.t === "hello" && role === "host") {           // a peer wants in
      chan.postMessage({ t: "welcome", from: myId });
      becomeLinked(m.from);
      $("pairState").innerHTML = '<i class="dot live"></i> SECURE LINK ESTABLISHED';
    } else if (m.t === "welcome" && role === "join") {  // host accepted us
      becomeLinked(m.from);
    } else if (m.t === "ping") {
      chan.postMessage({ t: "pong", from: myId, ts: m.ts });
      liveMessage("ping received");
    } else if (m.t === "pong") {
      $("connPing").textContent = (Date.now() - m.ts) + " ms";
      lastSeen = Date.now();
    } else if (m.t === "hb") {
      lastSeen = Date.now();
    } else if (m.t === "bye") {
      liveMessage("peer disconnected");
      teardownLink("Peer left");
    }
  }
  function becomeLinked(peer) {
    $("connState").textContent = "Linked · live";
    $("connState").classList.add("ok");
    $("connPeer").textContent = (role === "host" ? "peer-" : "host-") + peer;
    $("liveControls").hidden = false;
    $("liveMsg").hidden = false;
    liveMessage("link established");
    toast("Live link established", "ok");
    log("Live link established with " + peer, "ok");
    lastSeen = Date.now();
    clearInterval(hbTimer);
    hbTimer = setInterval(() => { if (chan) chan.postMessage({ t: "hb", from: myId }); }, 2000);
    clearInterval(aliveTimer);
    aliveTimer = setInterval(() => {
      if (Date.now() - lastSeen > 7000) { liveMessage("peer timed out"); teardownLink("Peer timed out"); }
    }, 2000);
  }
  function liveMessage(txt) {
    const d = new Date();
    $("liveMsg").textContent = "[" + pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds()) + "] " + txt;
  }
  function sendPing() {
    if (!chan) return;
    pingSent = Date.now();
    chan.postMessage({ t: "ping", from: myId, ts: pingSent });
    liveMessage("ping sent");
  }
  function teardownLink(reason) {
    clearInterval(hbTimer); clearInterval(aliveTimer);
    $("connState").textContent = "Not linked";
    $("connState").classList.remove("ok");
    $("connPeer").textContent = "—";
    $("connPing").textContent = "—";
    $("liveControls").hidden = true;
    if (reason) { toast(reason, "warn"); log("Live link ended: " + reason, "warn"); }
  }
  function closeChannel() {
    if (chan) { try { chan.close(); } catch (e) {} chan = null; }
  }
  function joinPair(code) {
    if (!/^\d{6}$/.test(code)) { toast("Enter a valid 6-digit code", "warn"); return; }
    openChannel(code, "join");
    $("scanMsg").textContent = "Linking to " + code + "…";
    log("Joining live channel " + code, "info");
    setTimeout(() => { if ($("connState").textContent.indexOf("Linked") < 0)
      toast("No host on that code. Open a second tab, press Show code, use that code.", "warn"); }, 1500);
  }
  $("joinBtn").addEventListener("click", () => joinPair(($("joinCode").value || "").trim()));
  $("pingBtn").addEventListener("click", sendPing);
  function updatePairCountdown() {
    const left = Math.max(0, pairExpiry - Date.now());
    const m = Math.floor(left / 60000), s = Math.floor((left % 60000) / 1000);
    $("pairCountdown").textContent = pad(m) + ":" + pad(s);
    if (left <= 0) { clearInterval(pairTimer); $("pairState").innerHTML = '<i class="dot"></i> CODE EXPIRED'; }
  }
  $("regenBtn").addEventListener("click", genPairing);

  // --- camera scanning ---
  let camStream = null, scanRAF = 0;
  const decodeCanvas = document.createElement("canvas");

  async function startCamera() {
    const msg = $("scanMsg");
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
      try {
        camStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" }, audio: false });
        const v = $("cam"); v.srcObject = camStream; await v.play();
        msg.textContent = "Scanning… hold a LinkControl X QR in view.";
        log("Camera opened for QR scan", "info");
        scanLoop(v);
        return;
      } catch (e) {
        msg.textContent = "Camera blocked or unavailable — opening photo capture instead.";
        log("Camera stream denied/unavailable: " + (e && e.name), "warn");
      }
    }
    $("qrfile").click(); // fallback: capture a photo, then decode
  }
  function scanLoop(v) {
    if (!camStream) return;
    if (v.readyState === v.HAVE_ENOUGH_DATA && typeof jsQR !== "undefined") {
      const w = v.videoWidth, h = v.videoHeight;
      if (w && h) {
        decodeCanvas.width = w; decodeCanvas.height = h;
        const cx = decodeCanvas.getContext("2d", { willReadFrequently: true });
        cx.drawImage(v, 0, 0, w, h);
        const code = jsQR(cx.getImageData(0, 0, w, h).data, w, h, { inversionAttempts: "dontInvert" });
        if (code && code.data) { stopCamera(); handleDecoded(code.data); return; }
      }
    }
    scanRAF = requestAnimationFrame(() => scanLoop(v));
  }
  $("qrfile").addEventListener("change", function () {
    const file = this.files && this.files[0]; if (!file) return;
    const img = new Image();
    img.onload = () => {
      const w = img.naturalWidth, h = img.naturalHeight;
      decodeCanvas.width = w; decodeCanvas.height = h;
      const cx = decodeCanvas.getContext("2d", { willReadFrequently: true });
      cx.drawImage(img, 0, 0);
      let code = null;
      if (typeof jsQR !== "undefined") code = jsQR(cx.getImageData(0, 0, w, h).data, w, h);
      URL.revokeObjectURL(img.src);
      if (code && code.data) handleDecoded(code.data);
      else $("scanMsg").textContent = "No QR code found in that photo — try again.";
    };
    img.onerror = () => { $("scanMsg").textContent = "Could not read that image."; };
    img.src = URL.createObjectURL(file);
    this.value = "";
  });
  function handleDecoded(text) {
    let codeStr = text, ok = false;
    try { const o = JSON.parse(text); if (o && o.app === "linkcontrol-x") { codeStr = o.c; ok = true; } } catch (e) {}
    const digits = (String(codeStr).match(/\d{6}/) || [""])[0];
    $("scanMsg").textContent = ok ? "LinkControl X code detected — linking…" : "QR read: " + String(text).slice(0, 40);
    log("QR decoded · " + (ok ? "code " + digits : "non-app QR"), ok ? "ok" : "warn");
    if (ok && digits) { $("joinCode").value = digits; joinPair(digits); }
    else toast("That QR is not a LinkControl X code", "warn");
  }
  function stopCamera() {
    if (scanRAF) cancelAnimationFrame(scanRAF), scanRAF = 0;
    if (camStream) { camStream.getTracks().forEach((t) => t.stop()); camStream = null; }
    const v = $("cam"); if (v) { v.srcObject = null; }
  }
  $("startCamBtn").addEventListener("click", startCamera);
  $("joinCode").addEventListener("input", function () { this.value = this.value.replace(/\D/g, "").slice(0, 6); });

  $("disconnectBtn").addEventListener("click", () => {
    if (chan) chan.postMessage({ t: "bye", from: myId });
    teardownLink(null);
    closeChannel();
    toast("Disconnected", "warn"); log("Live link disconnected by user", "warn");
    // re-host so the Show-code tab stays pairable
    if ($("pair-show") && !$("pair-show").classList.contains("hidden") && pairCode) openChannel(pairCode, "host");
  });

  /* ---------------- 9) SETTINGS ---------------- */
  function applyTheme() {
    document.documentElement.setAttribute("data-theme", settings.theme);
    qsa("#themes .swatch").forEach((s) => s.classList.toggle("active", s.dataset.theme === settings.theme));
    fx.restart();
  }
  qsa("#themes .swatch").forEach((s) => s.addEventListener("click", () => {
    settings.theme = s.dataset.theme; saveSettings(); applyTheme(); log("Theme set: " + settings.theme, "info");
  }));

  function syncToggles() {
    setToggle("tgMotion", settings.reduceMotion);
    setToggle("tgParticles", settings.particles);
    setToggle("tgHaptics", settings.haptics);
    document.body.classList.toggle("rm", settings.reduceMotion);
  }
  function setToggle(id, on) { const t = $(id); t.classList.toggle("on", !!on); t.setAttribute("aria-checked", on ? "true" : "false"); }
  $("tgMotion").addEventListener("click", () => { settings.reduceMotion = !settings.reduceMotion; saveSettings(); syncToggles(); fx.restart(); });
  $("tgParticles").addEventListener("click", () => { settings.particles = !settings.particles; saveSettings(); syncToggles(); fx.restart(); });
  $("tgHaptics").addEventListener("click", () => { settings.haptics = !settings.haptics; saveSettings(); syncToggles(); });

  $("permBtn").addEventListener("click", () => { $("permModal").hidden = false; });
  $("permClose").addEventListener("click", () => { $("permModal").hidden = true; });
  $("permModal").addEventListener("click", (e) => { if (e.target === $("permModal")) $("permModal").hidden = true; });

  $("resetBtn").addEventListener("click", () => {
    ["settings", "logs", "posture"].forEach(store.del);
    toast("Local data cleared", "ok");
    setTimeout(() => location.reload(), 500);
  });

  /* ---------------- LOGS rendering ---------------- */
  function renderLogs() {
    const logs = store.get("logs", []);
    if (!logs.length) { $("logList").innerHTML = '<p class="note">No activity yet.</p>'; return; }
    $("logList").innerHTML = logs.map((e) => {
      const d = new Date(e.ts);
      const ts = pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
      return '<div class="logrow ' + e.level + '"><span class="lv"></span><time>' + ts + "</time><span>" + escapeHtml(e.message) + "</span></div>";
    }).join("");
  }
  function escapeHtml(s) { return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }
  $("clearLogsBtn").addEventListener("click", () => { store.set("logs", []); renderLogs(); toast("Logs cleared", "ok"); });
  $("exportLogsBtn").addEventListener("click", async () => {
    const txt = store.get("logs", []).map((e) => new Date(e.ts).toISOString() + " [" + e.level + "] " + e.message).join("\n");
    try { await navigator.clipboard.writeText(txt); toast("Logs copied to clipboard", "ok"); }
    catch (e) { toast("Copy blocked by browser", "err"); }
  });

  /* ---------------- 10) PWA + INIT ---------------- */
  let deferredPrompt = null;
  window.addEventListener("beforeinstallprompt", (e) => { e.preventDefault(); deferredPrompt = e; $("installBtn").hidden = false; });
  $("installBtn").addEventListener("click", async () => {
    if (!deferredPrompt) { toast("Install from your browser menu (Add to Home screen)", "warn"); return; }
    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    log("PWA install prompt: " + outcome, "info");
    deferredPrompt = null; $("installBtn").hidden = true;
  });
  window.addEventListener("appinstalled", () => { toast("LinkControl X installed", "ok"); log("App installed", "ok"); });

  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("sw.js").catch((e) => log("Service worker registration failed", "warn"));
    });
  }

  function init() {
    applyTheme();
    syncToggles();
    renderDeviceInfo();
    startLiveMonitor();
    renderChecklistPending();
    startActivity();
    genPairing();
    // restore last posture, if any
    const p = store.get("posture", null);
    if (p) updatePosture(p.score, p.pass, p.warn, p.fail);
    log("LinkControl X started (local demo)", "info");
  }
  init();
})();
