package dev.wido.tv

/** Petits scripts que le lecteur envoie à la page (plein écran, épisode suivant, image, masquage…). */
internal object PageScripts {
    /** Le bouton plein écran visible du lecteur de la page (au premier plan à cet endroit), en fractions de l'écran. */
    const val FULLSCREEN_BUTTON = """(function () {
  var re = /(plein[ -]?(é|e)cran|full ?screen)/i;
  var els = document.querySelectorAll('button,[role=button],a,[class*=fullscreen],[class*=Fullscreen]');
  for (var i = 0; i < els.length; i++) {
    var e = els[i];
    var cls = typeof e.className === 'string' ? e.className : '';
    var label = (e.getAttribute('aria-label') || '') + ' ' + (e.getAttribute('title') || '') + ' ' + (e.getAttribute('data-title-no-tooltip') || '') + ' ' + cls;
    if (!re.test(label) || /(exit|quitter|sortir)/i.test(label)) continue;
    var r = e.getBoundingClientRect();
    if (r.width < 8 || r.height < 8) continue;
    var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    if (cx < 0 || cy < 0 || cx > innerWidth || cy > innerHeight) continue;
    var top = document.elementFromPoint(cx, cy);
    if (!top || !(top === e || e.contains(top))) continue;
    return JSON.stringify({ x: cx / innerWidth, y: cy / innerHeight });
  }
  return null;
})()"""

    const val KEEP_VISIBLE = """(function () {
  try {
    Object.defineProperty(document, 'hidden', { get: function () { return false; } });
    Object.defineProperty(document, 'visibilityState', { get: function () { return 'visible'; } });
    Object.defineProperty(document, 'webkitHidden', { get: function () { return false; } });
  } catch (e) {}
  ['visibilitychange', 'webkitvisibilitychange', 'blur'].forEach(function (t) {
    window.addEventListener(t, function (e) { e.stopImmediatePropagation(); }, true);
    document.addEventListener(t, function (e) { e.stopImmediatePropagation(); }, true);
  });
})()"""

    /**
     * Trouve (et clique si `go`) le lien ou bouton « épisode suivant » de la page : par son texte,
     * son libellé d'accessibilité ou son titre. Renvoie vrai s'il existe.
     */
    const val NEXT = """function (go) {
  var re = /((é|e)pisode suivant|next episode|prochain (é|e)pisode|next up)/i;
  var els = document.querySelectorAll('a,button,[role=button]');
  for (var i = 0; i < els.length; i++) {
    var e = els[i];
    var t = ((e.getAttribute('aria-label') || '') + ' ' + (e.getAttribute('title') || '') + ' ' + (e.textContent || '')).replace(/\s+/g, ' ');
    if (t.length > 120 || !re.test(t)) continue;
    var r = e.getBoundingClientRect();
    if (r.width === 0 && r.height === 0 && !e.href) continue;
    if (go) e.click();
    return true;
  }
  return false;
}"""

    /** La plus grande vidéo visible (sinon la plus grande iframe, où se cache souvent le lecteur), en fractions de l'écran. */
    const val VIDEO_RECT = """(function () {
  var best = null, area = 0;
  function visible(r) { return Math.max(0, Math.min(r.right, innerWidth) - Math.max(r.left, 0)) * Math.max(0, Math.min(r.bottom, innerHeight) - Math.max(r.top, 0)); }
  document.querySelectorAll('video').forEach(function (v) { var r = v.getBoundingClientRect(), a = visible(r); if (a > area) { area = a; best = r; } });
  if (!best) document.querySelectorAll('iframe').forEach(function (f) { var r = f.getBoundingClientRect(), a = visible(r); if (a > area) { area = a; best = r; } });
  if (!best || area < 20000) return null;
  return JSON.stringify({ x: Math.max(0, best.left) / innerWidth, y: Math.max(0, best.top) / innerHeight, r: Math.min(innerWidth, best.right) / innerWidth, b: Math.min(innerHeight, best.bottom) / innerHeight });
})()"""

    /** Image de la page : og:image, twitter:image, image_src, thumbnailUrl, puis l'affiche du lecteur (`poster`). */
    const val META = """(function () {
  function abs(u) { try { return u ? new URL(u, location.href).href : null; } catch (e) { return null; } }
  var m = document.querySelector('meta[property="og:image"],meta[property="og:image:url"],meta[name="twitter:image"],meta[itemprop="thumbnailUrl"],link[itemprop="thumbnailUrl"],link[rel="image_src"]');
  var u = m ? (m.content || m.href) : null;
  if (!u) document.querySelectorAll('script[type="application/ld+json"]').forEach(function (s) {
    if (u) return;
    try { var j = JSON.parse(s.textContent); var t = (Array.isArray(j) ? j[0] : j).thumbnailUrl; u = Array.isArray(t) ? t[0] : t; } catch (e) {}
  });
  if (!u) { var v = document.querySelector('video[poster]'); u = v ? v.getAttribute('poster') : null; }
  return abs(u);
})()"""

    /**
     * Masque l'élément sous le curseur, ou le calque qui le contient (position fixe, z-index élevé),
     * et renvoie un sélecteur CSS qui le retrouvera aux prochaines visites.
     */
    const val HIDE = """function (fx, fy) {
  var el = document.elementFromPoint(fx * innerWidth, fy * innerHeight);
  if (!el || el === document.body || el === document.documentElement) return null;
  var target = el;
  for (var cur = el; cur && cur !== document.body; cur = cur.parentElement) {
    var cs = getComputedStyle(cur);
    if (cs.position === 'fixed' || cs.position === 'sticky' || (parseInt(cs.zIndex, 10) || 0) >= 100) target = cur;
  }
  function selector(e) {
    var parts = [];
    while (e && e.nodeType === 1 && e !== document.body && parts.length < 5) {
      if (e.id && /^[A-Za-z][\w-]*$/.test(e.id)) { parts.unshift('#' + e.id); break; }
      var part = e.tagName.toLowerCase();
      var cls = Array.prototype.filter.call(e.classList, function (c) { return /^[A-Za-z][\w-]{0,39}$/.test(c); }).slice(0, 2);
      if (cls.length) part += '.' + cls.join('.');
      else if (e.parentElement) part += ':nth-child(' + (Array.prototype.indexOf.call(e.parentElement.children, e) + 1) + ')';
      parts.unshift(part);
      e = e.parentElement;
    }
    return parts.join(' > ');
  }
  var s = selector(target);
  if (!s) return null;
  target.style.setProperty('display', 'none', 'important');
  document.documentElement.style.overflow = '';
  if (document.body) document.body.style.overflow = '';
  return s;
}"""
}
