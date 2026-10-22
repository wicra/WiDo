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

  // ---------- Fenêtres surgissantes : bandeau de cookies, pop-up ----------
  var MODAL_HINTS = '[role=dialog],[aria-modal=true],dialog[open],[class*=cookie],[id*=cookie],[class*=consent],[id*=consent],' +
    '[class*=gdpr],[id*=gdpr],[class*=modal],[id*=modal],[class*=popup],[id*=popup],[class*=overlay],[class*=banner]';
  var CONSENT_WORDS = /cookie|consent|gdpr|cmp|privacy|rgpd/i;

  // La fenêtre au premier plan (au centre de l'écran, ou bandeau collé en bas), avec au moins un bouton ; sinon null.
  function topModal() {
    if (!isTop || !window.__widoPickModal) return null;
    var iw = window.innerWidth, ih = window.innerHeight;
    var nodes = document.querySelectorAll(MODAL_HINTS + ',body > div,body > section,body > aside');
    var cands = [];
    var els = [];
    for (var i = 0; i < nodes.length && i < 400; i++) {
      var el = nodes[i];
      var tag = el.tagName;
      if (tag === 'NAV' || tag === 'HEADER' || tag === 'FOOTER') continue;
      var cs = window.getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) < 0.05) continue;
      var dialogish = tag === 'DIALOG' || el.getAttribute('role') === 'dialog' || el.getAttribute('aria-modal') === 'true';
      if (!dialogish && cs.position !== 'fixed' && cs.position !== 'sticky') continue;
      var r = el.getBoundingClientRect();
      var w = Math.min(r.right, iw) - Math.max(r.left, 0);
      var h = Math.min(r.bottom, ih) - Math.max(r.top, 0);
      if (w < 120 || h < 40) continue;
      var consent = CONSENT_WORDS.test((el.id || '') + ' ' + (typeof el.className === 'string' ? el.className : ''));
      var centered = r.left < iw / 2 && r.right > iw / 2 && r.top < ih / 2 && r.bottom > ih / 2 && w * h >= 0.08 * iw * ih;
      var bottomBar = r.bottom >= ih - 12 && w >= 0.6 * iw && h >= 60;
      if (!centered && !bottomBar && !(consent && w * h >= 0.04 * iw * ih)) continue;
      if (!el.querySelector(SELECTOR)) continue;
      var z = parseInt(cs.zIndex, 10);
      cands.push({ z: isNaN(z) ? 0 : z, order: i });
      els.push(el);
    }
    var best = window.__widoPickModal(cands);
    return best < 0 ? null : els[best];
  }

  function usableIn(root) {
    var nodes = root.querySelectorAll(SELECTOR);
    var list = [];
    for (var i = 0; i < nodes.length; i++) {
      var r = nodes[i].getBoundingClientRect();
      if (usable(nodes[i], r)) list.push({ el: nodes[i], x: r.left + r.width / 2, y: r.top + r.height / 2, w: r.width, h: r.height });
    }
    return list;
  }

  // (fx, fy) : position du curseur en fraction de la fenêtre. Renvoie la cible en fractions, ou null.
  // Une fenêtre surgissante est ouverte : les flèches ne parcourent que ses boutons (le reste de la page
  // est derrière), et depuis l'extérieur le curseur la rejoint.
  window.__widoSnap = function (dir, fx, fy) {
    if (!isTop || !window.__widoPick) return null;
    var modal = topModal();
    var items = modal ? usableIn(modal) : [];
    if (!items.length) items = usableIn(document);
    var cx = fx * window.innerWidth, cy = fy * window.innerHeight;
    var index = window.__widoPick(dir, cx, cy, items);
    if (index < 0 && modal && items.length) {
      var m = modal.getBoundingClientRect();
      var outside = cx < m.left || cx > m.right || cy < m.top || cy > m.bottom;
      if (outside) index = window.__widoPickPrimary(items.map(function (it) { return it.el.innerText || it.el.value || it.el.getAttribute('aria-label') || ''; }));
    }
    if (index < 0) return null;
    mark(items[index].el);
    // Les cibles suivantes depuis celle-ci, dans les 4 directions, avec la même règle : l'appli s'y place
    // aussitôt au prochain appui (sans attendre la page), puis vérifie par cette même fonction.
    var t = items[index], iw = window.innerWidth, ih = window.innerHeight;
    var next = {};
    ['left', 'right', 'up', 'down'].forEach(function (d) {
      var j = window.__widoPick(d, t.x, t.y, items);
      if (j >= 0) next[d] = [items[j].x / iw, items[j].y / ih];
    });
    return JSON.stringify({ x: t.x / iw, y: t.y / ih, next: next });
  };
  window.__widoUnmark = unmark;

  // Défile ce qui se trouve sous le curseur : la zone défilante de la fenêtre surgissante (conditions
  // d'utilisation, cookies…), sinon la page. k : fraction de la hauteur visible (signée). Vrai si ça peut défiler.
  window.__widoScrollAt = function (fx, fy, k) {
    if (!isTop) return false;
    function by(el, dy, isRoot) {
      try { (isRoot ? window : el).scrollBy({ top: dy, behavior: 'instant' }); } catch (e) { if (isRoot) window.scrollBy(0, dy); else el.scrollTop += dy; }
    }
    var el = document.elementFromPoint(fx * window.innerWidth, fy * window.innerHeight);
    while (el && el !== document.body && el !== document.documentElement) {
      var oy = window.getComputedStyle(el).overflowY;
      if ((oy === 'auto' || oy === 'scroll' || oy === 'overlay') && el.scrollHeight > el.clientHeight + 2) {
        var can = k > 0 ? el.scrollTop + el.clientHeight < el.scrollHeight - 1 : el.scrollTop > 0;
        if (can) { by(el, k * el.clientHeight, false); return true; }
      }
      el = el.parentElement;
    }
    var se = document.scrollingElement || document.documentElement;
    var canPage = k > 0 ? se.scrollTop + window.innerHeight < se.scrollHeight - 1 : se.scrollTop > 0;
    if (canPage) { by(null, k * window.innerHeight, true); return true; }
    return false;
  };

  // La fenêtre apparaît ou disparaît : l'app amène le curseur sur son bouton principal, puis le ramène
  // où il était à la fermeture. Vérifié au plus une fois par seconde, et jamais page cachée.
  var lastModal = null;
  var modalTimer = 0;
  function checkModal() {
    modalTimer = 0;
    if (document.hidden || !window.WiDoBridge) return;
    var m = topModal();
    if (m === lastModal) return;
    lastModal = m;
    if (!m) { window.WiDoBridge.postMessage(JSON.stringify({ type: 'modal', gone: true })); return; }
    var items = usableIn(m);
    var iw = window.innerWidth, ih = window.innerHeight;
    var x = 0.5, y = 0.5;
    if (items.length) {
      var i = window.__widoPickPrimary(items.map(function (it) { return it.el.innerText || it.el.value || it.el.getAttribute('aria-label') || ''; }));
      if (i >= 0) { mark(items[i].el); x = items[i].x / iw; y = items[i].y / ih; }
    }
    window.WiDoBridge.postMessage(JSON.stringify({ type: 'modal', x: x, y: y }));
  }
  if (isTop && typeof MutationObserver === 'function') {
    new MutationObserver(function () {
      if (!modalTimer) modalTimer = setTimeout(checkModal, 1000);
    }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style', 'open', 'aria-hidden'] });
  }

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
    // Lecture ou pause décidée par la page (bouton du lecteur) : l'app le sait aussitôt (écran de pause).
    ['play', 'pause', 'ended'].forEach(function (name) {
      document.addEventListener(name, function (e) {
        var v = e.target;
        if (!v || v.tagName !== 'VIDEO' || v.clientWidth * v.clientHeight < 40000) return;
        try { bridge.postMessage(JSON.stringify({ type: 'event', s: state(v), top: isTop })); } catch (err) { /* ignoré */ }
      }, true);
    });
  }
})();
