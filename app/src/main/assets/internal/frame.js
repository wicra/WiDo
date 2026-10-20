// Script interne de WiDo, injecté dans la page principale et dans ses iframes.
// Il n'est jamais choisi par la configuration distante : voir docs/SECURITY.md.
(function () {
  'use strict';
  if (window.__widoFrame) return;
  window.__widoFrame = true;
  var isTop = window === window.top;

  // ---------- Curseur aimanté (page principale) ----------
  var SELECTOR = 'a[href],button,input,select,textarea,summary,video,[role=button],[role=link],[role=tab],' +
    '[role=menuitem],[role=checkbox],[onclick],[tabindex]:not([tabindex="-1"])';
  var marked = null;
  var markedOutline = '';
  var markedOffset = '';

  function usable(el, r) {
    if (r.width < 8 || r.height < 8) return false;
    if (r.bottom < 0 || r.top > window.innerHeight || r.right < 0 || r.left > window.innerWidth) return false;
    var cs = window.getComputedStyle(el);
    return cs.visibility !== 'hidden' && cs.display !== 'none' && cs.pointerEvents !== 'none' &&
      parseFloat(cs.opacity) > 0.05;
  }

  function unmark() {
    if (!marked) return;
    marked.style.outline = markedOutline;
    marked.style.outlineOffset = markedOffset;
    marked = null;
  }

  function mark(el) {
    unmark();
    marked = el;
    markedOutline = el.style.outline;
    markedOffset = el.style.outlineOffset;
    el.style.outline = '3px solid #1E90FF';
    el.style.outlineOffset = '2px';
  }

  // (fx, fy) : position du curseur en fraction de la fenêtre. Renvoie la cible en fractions, ou null.
  window.__widoSnap = function (dir, fx, fy) {
    if (!isTop || !window.__widoPick) return null;
    var nodes = document.querySelectorAll(SELECTOR);
    var items = [];
    var els = [];
    for (var i = 0; i < nodes.length; i++) {
      var r = nodes[i].getBoundingClientRect();
      if (!usable(nodes[i], r)) continue;
      items.push({ x: r.left + r.width / 2, y: r.top + r.height / 2, w: r.width, h: r.height });
      els.push(nodes[i]);
    }
    var index = window.__widoPick(dir, fx * window.innerWidth, fy * window.innerHeight, items);
    if (index < 0) return null;
    mark(els[index]);
    return JSON.stringify({ x: items[index].x / window.innerWidth, y: items[index].y / window.innerHeight });
  };
  window.__widoUnmark = unmark;

  // ---------- Lecteur vidéo (n'importe quelle frame) ----------
  function bestVideo() {
    var videos = document.querySelectorAll('video');
    var best = null;
    var bestArea = -1;
    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      var area = (v.videoWidth || 0) * (v.videoHeight || 0) || v.clientWidth * v.clientHeight;
      if (area > bestArea) {
        best = v;
        bestArea = area;
      }
    }
    return best;
  }

  function state(v) {
    return {
      paused: v.paused,
      t: v.currentTime || 0,
      d: isFinite(v.duration) ? v.duration : 0,
      vol: v.muted ? 0 : v.volume
    };
  }

  // cmd : 'state' | 'toggle' | 'play' | 'pause' | 'seek' (by, en secondes) | 'seekTo' (to, en secondes) | 'vol' (by, entre -1 et 1)
  function command(c) {
    var v = bestVideo();
    if (!v) return null;
    switch (c.cmd) {
      case 'toggle': if (v.paused) v.play(); else v.pause(); break;
      case 'play': v.play(); break;
      case 'pause': v.pause(); break;
      case 'seek': v.currentTime = Math.max(0, Math.min(isFinite(v.duration) ? v.duration : 1e9, v.currentTime + c.by)); break;
      case 'seekTo': v.currentTime = Math.max(0, Math.min(isFinite(v.duration) ? v.duration : 1e9, c.to)); break;
      case 'vol': v.muted = false; v.volume = Math.max(0, Math.min(1, v.volume + c.by)); break;
      default: break;
    }
    return state(v);
  }

  // Mode cinéma : la plus grande vidéo (ou, à défaut, l'iframe du lecteur) occupe tout l'écran, sur
  // fond noir, sans le reste de la page. Sert de plein écran quand le site ne peut pas l'ouvrir lui-même.
  function cinema(on) {
    var old = document.getElementById('__wido_cinema');
    if (old) old.remove();
    var marked = document.querySelectorAll('[data-wido-cinema]');
    for (var i = 0; i < marked.length; i++) marked[i].removeAttribute('data-wido-cinema');
    if (!on) return false;
    var best = null, area = 0;
    function pick(list) {
      for (var j = 0; j < list.length; j++) {
        var r = list[j].getBoundingClientRect(), a = r.width * r.height;
        if (a > area) { area = a; best = list[j]; }
      }
    }
    pick(document.querySelectorAll('video'));
    if (!best) pick(document.querySelectorAll('iframe'));
    if (!best || area < 10000) return false;
    best.setAttribute('data-wido-cinema', '1');
    var st = document.createElement('style');
    st.id = '__wido_cinema';
    st.textContent = '[data-wido-cinema]{position:fixed!important;top:0!important;left:0!important;right:0!important;bottom:0!important;' +
      'width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;margin:0!important;' +
      'border:0!important;transform:none!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important}' +
      'html,body{overflow:hidden!important}';
    (document.head || document.documentElement).appendChild(st);
    return true;
  }

  // Repli sans passerelle native : la page principale répond à evaluateJavascript.
  window.__widoCommand = function (json) {
    try {
      var m = JSON.parse(json);
      if (m.cmd === 'type') return window.__widoType ? String(window.__widoType(m.text, m.submit)) : null;
      if (m.cmd === 'cinema') return String(cinema(!!m.on));
      var s = command(m);
      return s ? JSON.stringify(s) : null;
    } catch (e) {
      return null;
    }
  };

  // Passerelle native (WebMessageListener) : n'existe que si l'app l'a installée.
  var bridge = window.WiDoBridge;
  if (bridge && typeof bridge.postMessage === 'function') {
    bridge.onmessage = function (event) {
      try {
        var m = JSON.parse(event.data);
        if (m.cmd === 'type') { if (window.__widoType) window.__widoType(m.text, m.submit); return; }
        if (m.cmd === 'cinema') { cinema(!!m.on); return; }
        var s = command(m);
        if (s) bridge.postMessage(JSON.stringify({ type: 'media', s: s, top: isTop }));
      } catch (e) { /* message illisible : ignoré */ }
    };
    bridge.postMessage(JSON.stringify({ type: 'hello', top: isTop }));
  }
})();
