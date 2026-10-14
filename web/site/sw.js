/* KAGE MONARK SYSTEM — service worker
   Shows system notifications, routes notification buttons back to the app,
   and displays pushed quest alerts if a push server is connected later. */
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", e => e.waitUntil(self.clients.claim()));

// Push from a server (optional): payload {title, body, tag, data:{kind, ref}}
self.addEventListener("push", e => {
  let d = {};
  try { d = e.data ? e.data.json() : {}; } catch { d = { body: e.data ? e.data.text() : "" }; }
  const data = d.data || {};
  e.waitUntil(self.registration.showNotification(d.title || "KAGE MONARK SYSTEM", {
    body: d.body || "A quest requires your attention.", tag: d.tag || "km-push", data, requireInteraction: true,
    icon: "icon-192.png", badge: "icon-192.png",
    actions: data.ref ? [{ action: "done", title: "Complete quest" }, { action: "snooze", title: "Snooze 10 min" }] : []
  }));
});

// Tap or button: focus the open app and tell it what to do, or open the app on that quest
self.addEventListener("notificationclick", e => {
  e.notification.close();
  const data = e.notification.data || {}, action = e.action || "open";
  e.waitUntil((async () => {
    const wins = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    if (wins.length) {
      const w = wins[0];
      try { await w.focus(); } catch {}
      w.postMessage({ type: "quest-action", action, kind: data.kind, ref: data.ref });
    } else if (data.ref) {
      await self.clients.openWindow(`./?quest=${encodeURIComponent((data.kind || "reminder") + ":" + data.ref)}&action=${encodeURIComponent(action)}`);
    } else {
      await self.clients.openWindow("./");
    }
  })());
});
