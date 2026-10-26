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
  // Un nom fait pour durer : pas les noms générés à chaque version du site (css-1x9f2k, jsx-482913, ad_8f3k2…).
  function stable(n) {
    if (!/^[A-Za-z][\w-]{0,39}$/.test(n)) return false;
    if (/^(css|sc|jsx|svelte|emotion|styled|tw)-/i.test(n)) return false;
    return (n.replace(/[^0-9]/g, '').length < 2) || n.length < 6;
  }
  function part(e) {
    if (e.id && stable(e.id)) return '#' + e.id;
    var tag = e.tagName.toLowerCase();
    var cls = Array.prototype.filter.call(e.classList, stable).slice(0, 3);
    if (cls.length) return tag + '.' + cls.join('.');
    var role = e.getAttribute('role');
    if (role && /^[a-z]{2,20}$/.test(role)) return tag + '[role="' + role + '"]';
    // En dernier recours, le rang parmi les éléments du même type (plus stable que le rang parmi tous).
    var n = 1;
    for (var sib = e.previousElementSibling; sib; sib = sib.previousElementSibling) if (sib.tagName === e.tagName) n++;
    return tag + ':nth-of-type(' + n + ')';
  }
  // Le sélecteur le plus court qui ne désigne que cet élément : on remonte d'un parent tant qu'il en désigne plusieurs.
  function selector(e) {
    var sel = part(e);
    var cur = e;
    for (var depth = 0; depth < 6; depth++) {
      var count = 0;
      try { count = document.querySelectorAll(sel).length; } catch (err) { return null; }
      if (count === 1 || sel.charAt(0) === '#') break;
      cur = cur.parentElement;
      if (!cur || cur === document.body || cur === document.documentElement) break;
      sel = part(cur) + ' ' + sel;
    }
    return sel.length <= 300 ? sel : null;
  }
  // Plusieurs signatures du même élément, pour le retrouver sur les autres pages du site, où ses voisins changent :
  // 1. le sélecteur unique de cette page ; 2. ses propres classes seules, si elles suffisent déjà à le désigner ;
  // 3. une iframe (pub) : celles du même domaine tiers, où qu'elles soient dans la page.
  var out = [];
  function add(sel) { if (sel && sel.length <= 300 && out.indexOf(sel) < 0) out.push(sel); }
  add(selector(target));
  // Ses classes, même s'il a un identifiant : une autre page du site ne reprend pas toujours l'identifiant.
  var cls = Array.prototype.filter.call(target.classList, stable).slice(0, 3);
  if (cls.length) {
    var own = target.tagName.toLowerCase() + '.' + cls.join('.');
    try { if (document.querySelectorAll(own).length === 1) add(own); } catch (err) { /* ignoré */ }
  }
  var frames = target.tagName === 'IFRAME' ? [target] : Array.prototype.slice.call(target.querySelectorAll('iframe'), 0, 3);
  frames.forEach(function (f) {
    // Pas une grande iframe (le lecteur vidéo du site peut être dans la fenêtre qu'on masque) : seulement les pubs.
    var r = f.getBoundingClientRect();
    if (r.width * r.height > 0.4 * innerWidth * innerHeight) return;
    try {
      var host = new URL(f.src, location.href).hostname;
      if (host && host !== location.hostname && /^[a-z0-9.-]+$/.test(host)) add('iframe[src*="' + host + '"]');
    } catch (err) { /* ignoré */ }
  });
  if (!out.length) return null;
  target.style.setProperty('display', 'none', 'important');
  document.documentElement.style.overflow = '';
  if (document.body) document.body.style.overflow = '';
  return JSON.stringify(out);
}"""
}
