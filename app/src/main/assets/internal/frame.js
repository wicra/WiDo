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

  function styleOk(cs) {
    return cs.visibility !== 'hidden' && cs.display !== 'none' && cs.pointerEvents !== 'none' && parseFloat(cs.opacity) > 0.05;
  }

  function matches(el, sel) {
    var f = el.matches || el.webkitMatchesSelector;
    try { return !!f && f.call(el, sel); } catch (e) { return false; }
  }

  function closest(el, sel) {
    for (; el && el.nodeType === 1; el = el.parentElement) if (matches(el, sel)) return el;
    return null;
  }

  function isPage(el) {
    return !el || el === document.body || el === document.documentElement;
  }

  // Cartes cliquables qui ne sont ni lien ni bouton (affiches de films en <div> avec un clic JavaScript) : on ne
  // les reconnaît qu'au curseur « main » que le site leur donne. CARD_SEL limite la recherche aux noms usuels.
  var CARD_SEL = '.swiper-slide,.slick-slide,.splide__slide,.glide__slide,.owl-item,.keen-slider__slide,.embla__slide,' +
    '[class*=card],[class*=Card],[class*=poster],[class*=Poster],[class*=tile],[class*=Tile],[class*=thumb],[class*=Thumb],' +
    '[class*=item],[class*=Item],[class*=movie],[class*=Movie],[class*=film],[class*=episode],[class*=Episode],li,article,img';

  // La carte « main » qui contient el : l'ancêtre le plus haut au curseur pointer (le curseur s'hérite), de taille
  // d'une carte, ni dans un lien ni couvert par un lien à lui. null sinon.
  function cardOf(el) {
    var iw = window.innerWidth, ih = window.innerHeight;
    var top = null;
    for (var a = el, depth = 0; a && a.nodeType === 1 && !isPage(a) && depth < 8; a = a.parentElement, depth++) {
      if (window.getComputedStyle(a).cursor !== 'pointer') break;
      top = a;
    }
    if (!top || closest(top, SELECTOR)) return null;
    var r = top.getBoundingClientRect();
    if (r.width < 40 || r.height < 40 || r.width > iw * 0.7 || r.height > ih * 0.8) return null;
    var inner = top.querySelectorAll(SELECTOR);
    for (var i = 0; i < inner.length && i < 20; i++) {
      var q = inner[i].getBoundingClientRect();
      if (q.width * q.height >= r.width * r.height * 0.5) return null; // le lien qu'elle contient suffit
    }
    return top;
  }

  // Les éléments cliquables de root : liens, boutons… et les cartes « main ».
  function clickables(root, max) {
    var list = Array.prototype.slice.call(root.querySelectorAll(SELECTOR), 0, max);
    var cards = pageCards();
    for (var i = 0; i < cards.length; i++) if (root === document || root.contains(cards[i])) list.push(cards[i]);
    return list;
  }

  // Les cartes « main » de la page. Gardées tant que la page ne change pas (ni DOM, ni défilement, au plus 3 s) :
  // les appuis suivants ne les recherchent pas.
  var cardsMemo = null;
  var cardsAt = 0;
  function pageCards() {
    if (cardsMemo && Date.now() - cardsAt < 3000) return cardsMemo;
    cardsAt = Date.now();
    var list = [];
    var cands = document.querySelectorAll(CARD_SEL);
    var seen = typeof Set === 'function' ? new Set() : null;
    var ext = window.innerHeight * 1.5;
    for (var i = 0, n = 0; i < cands.length && n < 400; i++) {
      var c = cands[i];
      if (c.closest && c.closest('a[href],button')) continue; // déjà couvert par un lien : rien à calculer
      var cr = c.getBoundingClientRect();
      if (cr.bottom < -ext || cr.top > window.innerHeight + ext || cr.width < 8) continue; // loin de l'écran (en hauteur)
      n++;
      if (window.getComputedStyle(c).cursor !== 'pointer') continue; // une lecture de style, le plus souvent la seule
      var card = cardOf(c);
      if (!card || (seen ? seen.has(card) : list.indexOf(card) >= 0)) continue;
      if (seen) seen.add(card);
      list.push(card);
    }
    cardsMemo = list;
    return list;
  }

  // Pour un appel : une fonction qui donne la partie visible d'un élément, rognée par la fenêtre ET par ses
  // ancêtres en overflow ≠ visible (piste d'un carrousel, liste défilante). Chaque ancêtre n'est lu qu'une fois.
  // Approximations : un élément en position absolue échappe en vrai au rognage de ses ancêtres non positionnés.
  // reach : la fenêtre est prolongée d'autant d'écrans en haut et en bas (cibles à atteindre en défilant la page).
  function makeClip(reach) {
    var memo = typeof WeakMap === 'function' ? new WeakMap() : null;
    var ext = (reach || 0) * window.innerHeight;
    var vp = { l: 0, t: -ext, r: window.innerWidth, b: window.innerHeight + ext };
    function boxFor(node) {
      if (!node || node.nodeType !== 1 || isPage(node)) return vp;
      var m = memo && memo.get(node);
      if (m) return m;
      var cs = window.getComputedStyle(node);
      var c = cs.position === 'fixed' ? vp : boxFor(node.parentElement);
      var ox = cs.overflowX !== 'visible';
      var oy = cs.overflowY !== 'visible';
      if ((ox || oy) && cs.display !== 'inline' && cs.display !== 'contents') {
        var r = node.getBoundingClientRect();
        var l = r.left + node.clientLeft;
        var t = r.top + node.clientTop;
        c = {
          l: ox ? Math.max(c.l, l) : c.l, t: oy ? Math.max(c.t, t) : c.t,
          r: ox ? Math.min(c.r, l + node.clientWidth) : c.r, b: oy ? Math.min(c.b, t + node.clientHeight) : c.b
        };
      }
      if (memo) memo.set(node, c);
      return c;
    }
    return function (el, r, cs) {
      var c = cs.position === 'fixed' ? vp : boxFor(el.parentElement);
      var l = Math.max(r.left, c.l), t = Math.max(r.top, c.t), rr = Math.min(r.right, c.r), b = Math.min(r.bottom, c.b);
      return { l: l, t: t, w: Math.max(0, rr - l), h: Math.max(0, b - t) };
    };
  }

  // Un élément cliquable mesuré : r = boîte entière, (x, y, w, h) = centre et taille de sa partie VISIBLE,
  // vis = part visible (0 à 1). anywhere : aussi hors écran (membres d'un carrousel). null si inutilisable.
  function measure(el, clip, anywhere) {
    var r = el.getBoundingClientRect();
    if (r.width < 8 || r.height < 8) return null;
    if (!anywhere && (r.bottom < 0 || r.top > window.innerHeight || r.right < 0 || r.left > window.innerWidth)) return null;
    var cs = window.getComputedStyle(el);
    if (!styleOk(cs)) return null;
    var v = clip(el, r, cs);
    return { el: el, r: r, x: v.l + v.w / 2, y: v.t + v.h / 2, w: v.w, h: v.h, vis: (v.w * v.h) / (r.width * r.height) };
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

  // ---------- Carrousels ----------
  // Racines connues des bibliothèques à translate3d (Swiper, Slick, Splide, Glide, Owl, Flickity, keen-slider, Embla…).
  var SLIDER_SEL = '.swiper,.swiper-container,swiper-container,.slick-slider,.splide,.glide,.owl-carousel,.flickity-enabled,' +
    '.keen-slider,.embla,[aria-roledescription=carousel],[class*=carousel],[class*=Carousel],[class*=slider],[class*=Slider]';
  var NAV_SEL = 'button,a,[role=button],[aria-label],[class*=next],[class*=Next],[class*=prev],[class*=Prev],' +
    '[class*=arrow],[class*=Arrow],[class*=chevron],[class*=control]';
  var pending = null; // défilement animé par le site en cours : { el (cible visée ou null), from, dir, pos }

  function sliderRoot(box) {
    var a = box;
    for (var i = 0; i < 4 && a && !isPage(a); i++, a = a.parentElement) if (matches(a, SLIDER_SEL)) return a;
    return null;
  }

  function transformedBetween(el, box) {
    for (var a = el.parentElement; a && a !== box; a = a.parentElement) {
      if (window.getComputedStyle(a).transform !== 'none') return true;
    }
    return false;
  }

  // Une rangée de cartes : au moins deux autres cliquables de même hauteur que el sur sa bande, dont un
  // hors de la boîte. Écarte un en-tête en overflow:hidden qui cache un menu hors écran.
  function rowLike(box, el, er) {
    var br = box.getBoundingClientRect();
    var nodes = clickables(box, 300);
    var same = 0, out = 0;
    for (var i = 0; i < nodes.length && i < 300; i++) {
      if (nodes[i] === el) continue;
      var r = nodes[i].getBoundingClientRect();
      var cy = r.top + r.height / 2;
      if (r.width < 8 || cy < er.top || cy > er.bottom || Math.abs(r.height - er.height) > er.height * 0.25) continue;
      same++;
      if (r.left >= br.right - 1 || r.right <= br.left + 1) out++;
    }
    return same >= 2 && out >= 1;
  }

  // Le carrousel horizontal qui contient el, ou null : { box : l'élément qui rogne (et défile si scroll),
  // root : où chercher ses boutons, scroll : on le fait défiler par scrollLeft, drag : un glisser du doigt peut l'avancer }.
  function carouselOf(el) {
    var ih = window.innerHeight;
    var er = el.getBoundingClientRect();
    for (var a = el.parentElement, depth = 0; a && !isPage(a) && depth < 25; a = a.parentElement, depth++) {
      var cs = window.getComputedStyle(a);
      var ox = cs.overflowX;
      if (ox === 'visible' || ox === 'clip' || cs.display === 'inline' || a.clientWidth < er.width * 1.5) continue;
      // Un conteneur de toute la page en overflow-x:hidden (menus hors écran…) n'est pas un carrousel.
      if (a.getBoundingClientRect().height > ih * 0.9) continue;
      var wide = a.scrollWidth > a.clientWidth + 4;
      if (ox !== 'hidden') {
        if (wide) return { box: a, root: a, scroll: true, drag: false }; // overflow-x auto / scroll (scroll-snap compris)
        continue;
      }
      // overflow-x:hidden : piste déplacée par transform (bibliothèque) → son API, ses boutons ou un glisser.
      // Sans transform ni classe connue (carrousel maison à scrollLeft… ou en-tête qui cache un menu) : ses
      // boutons seulement, on ne touche jamais à son scrollLeft.
      if (!rowLike(a, el, er)) continue;
      var root = sliderRoot(a);
      var moved = transformedBetween(el, a);
      if (root || moved || wide) return { box: a, root: root || a.parentElement || a, scroll: false, drag: !!(root || moved) };
    }
    return null;
  }

  // Les cliquables du carrousel, où qu'ils soient (rognés, hors écran), sauf cur et ce qui le contient ou qu'il contient.
  // Seulement les cartes de même hauteur (± 40 %) : pas les flèches du carrousel ni les petits boutons des cartes.
  function members(car, cur, clip) {
    var nodes = clickables(car.box, 600);
    var list = [];
    var h = cur.r.height;
    for (var i = 0; i < nodes.length && i < 600; i++) {
      var n = nodes[i];
      if (n === cur.el || n.contains(cur.el) || cur.el.contains(n)) continue;
      var it = measure(n, clip, true);
      if (it && Math.abs(it.r.height - h) <= h * 0.4) list.push(it);
    }
    return list;
  }

  // Le prochain élément de la même rangée du carrousel dans cette direction (positions réelles, même cachées), ou null.
  function nextInRow(dir, cur, car, clip) {
    var list = members(car, cur, clip);
    var pts = list.map(function (it) { return { x: it.r.left + it.r.width / 2, y: it.r.top + it.r.height / 2, w: it.r.width, h: it.r.height }; });
    var r = cur.r;
    var j = window.__widoPickInRow(dir, r.left + r.width / 2, r.top + r.height / 2, pts, r.top, r.bottom);
    return j < 0 ? null : list[j];
  }

  function instantScroll(el, dx, dy) {
    var page = isPage(el);
    var target = page ? document.documentElement : el;
    var old = target.style.scrollBehavior;
    target.style.scrollBehavior = 'auto'; // un scroll-behavior:smooth du site rendrait la position fausse au retour
    if (page) window.scrollBy(dx, dy);
    else { el.scrollLeft += dx; el.scrollTop += dy; }
    target.style.scrollBehavior = old;
  }

  // Ce qu'il faut faire défiler pour voir t en entier : [{ el (null = la page), x, y }]. Rien pour ce qui est fixe ou collant.
  function revealPlan(t) {
    var ops = [];
    var iw = window.innerWidth, ih = window.innerHeight;
    var r = t.el.getBoundingClientRect();
    var car = carouselOf(t.el);
    if (car && car.scroll) {
      var b = car.box, br = b.getBoundingClientRect();
      var l = Math.max(0, br.left + b.clientLeft), rr = Math.min(iw, br.left + b.clientLeft + b.clientWidth);
      var dx = window.__widoRevealDelta(r.left, r.right, l, rr, Math.min(48, (rr - l) * 0.08));
      dx = Math.max(-b.scrollLeft, Math.min(b.scrollWidth - b.clientWidth - b.scrollLeft, dx));
      if (Math.abs(dx) >= 1) ops.push({ el: b, x: dx, y: 0 });
    }
    var sc = null;
    for (var a = t.el; a && !isPage(a); a = a.parentElement) {
      var cs = window.getComputedStyle(a);
      if (cs.position === 'fixed' || cs.position === 'sticky') return ops; // la page qui défile ne le déplacerait pas
      var oy = cs.overflowY;
      if (!sc && a !== t.el && (oy === 'auto' || oy === 'scroll' || oy === 'overlay') && a.scrollHeight > a.clientHeight + 2 &&
        // overflow-x:auto force overflow-y:auto : une rangée qui déborde de quelques px (zoom au survol) n'est pas une liste verticale
        !(a.scrollWidth > a.clientWidth + 4 && a.scrollHeight < a.clientHeight * 1.5)) sc = a;
    }
    var vs = 0, ve = ih, pad = Math.round(ih * 0.1); // marge : un bandeau collé en haut ne le cache pas
    var se = document.scrollingElement || document.documentElement;
    var top = se.scrollTop, max = se.scrollHeight - ih;
    if (sc) {
      var sr = sc.getBoundingClientRect();
      vs = Math.max(0, sr.top + sc.clientTop);
      ve = Math.min(ih, sr.top + sc.clientTop + sc.clientHeight);
      pad = Math.min(24, (ve - vs) * 0.05);
      top = sc.scrollTop; max = sc.scrollHeight - sc.clientHeight;
    }
    var dy = window.__widoRevealDelta(r.top, r.bottom, vs, ve, pad);
    dy = Math.max(-top, Math.min(max - top, dy));
    if (Math.abs(dy) >= 1) ops.push({ el: sc, x: 0, y: dy });
    return ops;
  }

  function reveal(t) {
    var ops = revealPlan(t);
    for (var i = 0; i < ops.length; i++) instantScroll(ops[i].el, ops[i].x, ops[i].y);
    return ops.length > 0;
  }

  // Le bouton « suivant » (fwd) ou « précédent » du carrousel, cherché dans sa racine et jusqu'à 3 parents,
  // dans la bande verticale du carrousel (pas celui d'une autre rangée). Même invisible (flèches au survol).
  function navButton(car, fwd) {
    var br = car.box.getBoundingClientRect();
    var mid = br.left + br.width / 2;
    var want = fwd ? 1 : -1;
    var best = null, bestScore = 0;
    var scope = car.root;
    for (var up = 0; up < 4 && scope && !isPage(scope); up++, scope = scope.parentElement) {
      var nodes = scope.querySelectorAll(NAV_SEL);
      for (var i = 0; i < nodes.length && i < 400; i++) {
        var b = nodes[i];
        if (b.contains(car.box) || b.disabled || b.getAttribute('aria-disabled') === 'true') continue;
        var href = b.tagName === 'A' ? (b.getAttribute('href') || '') : '';
        if (href && href.charAt(0) !== '#' && href.indexOf('javascript:') !== 0) continue; // un vrai lien (affiche) : jamais
        var cls = b.getAttribute('class') || '';
        if (/disabled|lock/i.test(cls)) continue; // bout atteint (swiper-button-disabled, slick-disabled…)
        var r = b.getBoundingClientRect();
        if (r.width > 0 && (r.bottom < br.top - 40 || r.top > br.bottom + 40)) continue; // une autre rangée
        var text = (b.textContent || '').trim();
        var hint = window.__widoNavDir(cls + ' ' + (b.getAttribute('aria-label') || '') + ' ' + (b.getAttribute('title') || '') +
          ' ' + (text.length <= 3 ? text : ''));
        var score = 0;
        if (hint === want) score = 3;
        else if (hint === 0 && r.width > 0 && r.width <= 120 && text.length <= 3 &&
          (matches(b, 'button,[role=button]') || /arrow|chevron|control/i.test(cls)) &&
          (fwd ? r.left > mid : r.right < mid)) score = 1; // petite flèche sans nom, du bon côté
        if (score > bestScore) { bestScore = score; best = b; }
      }
      if (bestScore === 3) break;
    }
    return best;
  }

  // Fait avancer un carrousel à translate3d. 'instant' : c'est fait (API sans animation) ;
  // 'async' : le site anime ; false : rien trouvé (l'appli peut glisser le doigt).
  function advance(car, fwd) {
    var root = car.root;
    var $ = window.jQuery;
    try {
      var sw = root.swiper || car.box.swiper || (root.parentElement && root.parentElement.swiper);
      if (sw && typeof sw.slideNext === 'function') { if (fwd) sw.slideNext(0); else sw.slidePrev(0); return 'instant'; }
      var Fl = window.Flickity;
      var fl = Fl && Fl.data && (Fl.data(root) || Fl.data(car.box) || Fl.data(car.box.parentElement));
      if (fl) { if (fwd) fl.next(false, true); else fl.previous(false, true); return 'instant'; }
      if ($ && $.fn) {
        var s = $(root).closest('.slick-initialized');
        if (s.length && $.fn.slick) { s.slick(fwd ? 'slickNext' : 'slickPrev'); return 'async'; }
        var o = $(root).closest('.owl-carousel');
        if (o.length) { o.trigger(fwd ? 'next.owl.carousel' : 'prev.owl.carousel', [0]); return 'async'; }
      }
    } catch (e) { /* API absente ou d'une autre version : on passe aux boutons */ }
    var b = navButton(car, fwd);
    if (b) { b.click(); return 'async'; }
    return false;
  }

  // ---------- Choix de la cible ----------
  // reach : comme pour makeClip (0 : ce qui est à l'écran).
  function usableIn(root, clip, reach) {
    clip = clip || makeClip(reach); // checkModal() l'appelle sans
    var nodes = clickables(root);
    var list = [];
    var ext = (reach || 0) * window.innerHeight;
    for (var i = 0; i < nodes.length; i++) {
      var r = nodes[i].getBoundingClientRect();
      if (r.bottom < -ext || r.top > window.innerHeight + ext) continue;
      var it = measure(nodes[i], clip, true);
      if (it && it.w >= 8 && it.h >= 8) list.push(it); // il en reste assez de visible pour viser et toucher
    }
    return list;
  }

  // L'élément où est le curseur : celui marqué s'il est encore sous le curseur, sinon celui sous le curseur.
  function currentAt(cx, cy, clip) {
    var el = null;
    if (marked && document.documentElement.contains(marked)) {
      var r = marked.getBoundingClientRect();
      if (cx >= r.left - 4 && cx <= r.right + 4 && cy >= r.top - 4 && cy <= r.bottom + 4) el = marked;
    }
    if (!el) {
      var under = document.elementFromPoint(cx, cy);
      el = closest(under, SELECTOR) || (under && cardOf(under));
    }
    return el ? measure(el, clip, true) : null;
  }

  function context(modal) {
    var clip = makeClip();
    var items = modal ? usableIn(modal, clip) : [];
    if (!items.length) { modal = null; items = usableIn(document, clip); }
    return { modal: modal, clip: clip, items: items };
  }

  // Cibles suivantes depuis t, pour que l'appli y aille sans attendre la page. Seulement celles qui n'exigent
  // aucun défilement : une cible à révéler ou un bout de carrousel, c'est la page qui décide.
  function nextFrom(t, ctx) {
    var iw = window.innerWidth, ih = window.innerHeight;
    var next = {};
    var car = ctx.modal ? null : carouselOf(t.el);
    ['left', 'right', 'up', 'down'].forEach(function (d) {
      var q = null;
      if (car && (d === 'left' || d === 'right')) q = nextInRow(d, t, car, ctx.clip);
      else {
        var j = window.__widoPick(d, t.x, t.y, ctx.items);
        if (j >= 0) q = ctx.items[j];
      }
      if (q && q.vis >= 0.98 && !revealPlan(q).length) next[d] = [q.x / iw, q.y / ih];
    });
    return next;
  }

  // Le curseur va sur t : défilement s'il le faut (instantané), nouvelle mesure, marquage, réponse à l'appli.
  function land(t, ctx, extra) {
    if (reveal(t)) {
      var fresh = context(ctx.modal);
      ctx = fresh;
      t = measure(t.el, ctx.clip, true) || t;
      // Un scroll-snap a pu ramener la piste ailleurs : le navigateur fait au mieux.
      if (t.vis < 0.5 && t.el.scrollIntoView) {
        try { t.el.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch (e) { t.el.scrollIntoView(false); }
        ctx = context(ctx.modal);
        t = measure(t.el, ctx.clip, true) || t;
      }
    }
    mark(t.el);
    var o = { x: t.x / window.innerWidth, y: t.y / window.innerHeight, next: nextFrom(t, ctx) };
    if (extra) for (var k in extra) o[k] = extra[k];
    return JSON.stringify(o);
  }

  function stay(cur, extra) {
    mark(cur.el);
    var o = { x: cur.x / window.innerWidth, y: cur.y / window.innerHeight, next: {} };
    for (var k in extra) o[k] = extra[k];
    return JSON.stringify(o);
  }

  // ◀ ▶ dans un carrousel. Renvoie la réponse, ou null si cur n'est pas dans un carrousel (règle générale).
  function snapInCarousel(dir, cur, ctx) {
    var car = carouselOf(cur.el);
    if (!car) return null;
    var fwd = dir === 'right';
    var t = nextInRow(dir, cur, car, ctx.clip);
    if (t && t.vis >= 0.98) return land(t, ctx);
    if (car.scroll) {
      var b = car.box;
      if (t) return land(t, ctx); // révélé par scrollLeft dans land()
      // Plus rien dans le DOM de ce côté, mais la piste peut encore défiler (liste chargée au fur et à mesure).
      var room = fwd ? b.scrollWidth - b.clientWidth - b.scrollLeft : b.scrollLeft;
      if (room > 8) {
        instantScroll(b, (fwd ? 1 : -1) * Math.min(room, b.clientWidth * 0.8), 0);
        pending = { el: null, from: cur.el, dir: dir, pos: null };
        return stay(measure(cur.el, makeClip(), true) || cur, { wait: 200 });
      }
    } else {
      var how = advance(car, fwd);
      if (how === 'instant') {
        ctx = context(null);
        var again = t && measure(t.el, ctx.clip, true);
        if (again && again.w >= 8) return land(again, ctx);
        var c2 = measure(cur.el, ctx.clip, true);
        var t2 = c2 && nextInRow(dir, c2, car, ctx.clip);
        if (t2 && t2.w >= 8) return land(t2, ctx);
        if (c2 && c2.w >= 8) return stay(c2, { edge: true });
      } else if (how === 'async' || (t && car.drag)) {
        pending = { el: t ? t.el : null, from: cur.el, dir: dir, pos: null };
        var extra = { wait: 350 };
        if (!how) {
          // Ni API ni bouton : l'appli glisse le doigt sur la piste, d'environ une affiche (pas au-delà du bout).
          var v = measure(car.box, ctx.clip, true) || cur;
          var len = Math.min(v.w * 0.6, Math.max(cur.r.width * 1.1, 80));
          var x0 = fwd ? v.x + len / 2 : v.x - len / 2;
          var iw = window.innerWidth, ih = window.innerHeight;
          extra = { wait: 250, drag: [x0 / iw, cur.y / ih, (fwd ? x0 - len : x0 + len) / iw, cur.y / ih] };
        }
        return stay(cur, extra);
      }
    }
    // Bout de la rangée : un lien juste à côté sur la même bande (« Tout voir › »), sinon on ne bouge pas.
    var br = car.box.getBoundingClientRect();
    var outside = ctx.items.filter(function (it) { return !car.box.contains(it.el); });
    var j = window.__widoPickInRow(dir, cur.x, cur.y, outside, br.top, br.bottom);
    if (j >= 0) return land(outside[j], ctx);
    return stay(cur, { edge: true });
  }

  // (fx, fy) : position du curseur en fraction de la fenêtre. Renvoie la cible en fractions, ou null.
  // Une fenêtre surgissante est ouverte : les flèches ne parcourent que ses boutons (le reste de la page
  // est derrière), et depuis l'extérieur le curseur la rejoint.
  // Réponse : { x, y, next, edge? (bout d'un carrousel : ne pas bouger), wait? (ms avant __widoSnapSettle),
  // drag? ([x0, y0, x1, y1] à glisser avant) }.
  window.__widoSnap = function (dir, fx, fy) {
    if (!isTop || !window.__widoPick) return null;
    pending = null;
    var ctx = context(topModal());
    var items = ctx.items;
    var cx = fx * window.innerWidth, cy = fy * window.innerHeight;
    if (!ctx.modal && (dir === 'left' || dir === 'right')) {
      var cur = currentAt(cx, cy, ctx.clip);
      var res = cur && snapInCarousel(dir, cur, ctx);
      if (res) return res;
    }
    var index = window.__widoPick(dir, cx, cy, items);
    if (index < 0 && ctx.modal && items.length) {
      var m = ctx.modal.getBoundingClientRect();
      var outside = cx < m.left || cx > m.right || cy < m.top || cy > m.bottom;
      if (outside) index = window.__widoPickPrimary(items.map(function (it) { return it.el.innerText || it.el.value || it.el.getAttribute('aria-label') || ''; }));
    }
    if (index < 0 && !ctx.modal && (dir === 'up' || dir === 'down')) {
      // Rien à l'écran au-dessus / en dessous : la rangée suivante jusqu'à un écran plus loin, que land() fait
      // venir à l'écran. Au-delà (texte sans liens), null : l'appli défile d'une demi-page comme avant.
      var far = usableIn(document, makeClip(1), 1);
      var k = window.__widoPick(dir, cx, cy, far);
      if (k >= 0) return land(far[k], ctx);
    }
    if (index < 0) return null;
    return land(items[index], ctx);
  };

  // Après une animation du site (bouton, API, glisser) : où va le curseur ? { wait } tant que ça bouge encore,
  // puis la réponse habituelle ; null si plus rien à faire. last : dernier essai, on répond quoi qu'il arrive.
  window.__widoSnapSettle = function (last) {
    var p = pending;
    if (!p || !window.__widoPick) return null;
    var ref = (p.el && document.documentElement.contains(p.el)) ? p.el : p.from;
    if (!document.documentElement.contains(ref)) { pending = null; return null; }
    var pos = ref.getBoundingClientRect().left;
    if (!last && (p.pos === null || Math.abs(pos - p.pos) > 1)) { p.pos = pos; return JSON.stringify({ wait: 120 }); }
    pending = null;
    var ctx = context(null);
    var t = p.el && measure(p.el, ctx.clip, true);
    if (t && t.vis >= 0.5) return land(t, ctx);
    var from = measure(p.from, ctx.clip, true);
    var car = from && carouselOf(from.el);
    var q = car && nextInRow(p.dir, from, car, ctx.clip);
    if (q && q.w >= 8) return land(q, ctx);
    if (t && t.w >= 8) return land(t, ctx);
    if (from && from.w >= 8) return stay(from, { edge: true });
    return null;
  };
  window.__widoUnmark = unmark;

  // « Épisode suivant » de la page (lien ou bouton du site), pour le proposer à la fin du film dans le lecteur
  // natif. Seulement un texte (ou nom, classe) qui parle d'épisode : jamais un simple « Suivant » (pages, carrousels).
  // click : le suit. Renvoie son libellé, ou null.
  var NEXT_EP = /(?:[ée]pisode|ep\.?)\s*suivant|prochain\s+[ée]pisode|next\s*ep(?:isode)?\b|episode\s*next|suivant\s*:\s*[ée]pisode/i;
  var NEXT_EP_CLASS = /next[-_]?ep(?:isode)?|episode[-_]?next|nextEpisode|NextEpisode/;
  window.__widoNextEpisode = function (click) {
    if (!isTop) return null;
    var nodes = document.querySelectorAll('a[href],button,[role=button],[role=link],[class*=next],[class*=Next],[id*=next],[id*=Next]');
    var best = null, bestScore = 0;
    for (var i = 0; i < nodes.length && i < 1500; i++) {
      var el = nodes[i];
      if (el.disabled || el.getAttribute('aria-disabled') === 'true') continue;
      var text = (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
      var name = (el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('title') || '');
      var cls = (typeof el.className === 'string' ? el.className : '') + ' ' + (el.id || '');
      var score = 0;
      if (text.length <= 80 && NEXT_EP.test(text)) score = 3;
      else if (NEXT_EP.test(name)) score = 2;
      else if (NEXT_EP_CLASS.test(cls) && matches(el, 'a[href],button,[role=button]')) score = 1;
      if (!score) continue;
      if (el.tagName === 'A') {
        var href = el.getAttribute('href') || '';
        if (/^javascript:/i.test(href) && !el.onclick) continue;
      }
      if (score > bestScore) { best = el; bestScore = score; if (score === 3) break; }
    }
    if (!best) return null;
    var label = ((best.innerText || '').replace(/\s+/g, ' ').trim() || best.getAttribute('aria-label') || best.getAttribute('title') || '').slice(0, 60);
    if (click) best.click();
    return label || 'next';
  };

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

  // Curseur maintenu contre un bord gauche/droit : fait défiler la rangée défilante sous lui (overflow-x auto /
  // scroll), sinon rien (faux : l'appli fait défiler la page comme avant). px : décalage signé.
  window.__widoScrollXAt = function (fx, fy, px) {
    if (!isTop) return false;
    var el = document.elementFromPoint(fx * window.innerWidth, fy * window.innerHeight);
    for (; el && !isPage(el); el = el.parentElement) {
      var ox = window.getComputedStyle(el).overflowX;
      if ((ox === 'auto' || ox === 'scroll' || ox === 'overlay') && el.scrollWidth > el.clientWidth + 2) {
        var can = px > 0 ? el.scrollLeft + el.clientWidth < el.scrollWidth - 1 : el.scrollLeft > 0;
        if (can) { instantScroll(el, px, 0); return true; }
      }
    }
    // Piste déplacée par transform (overflow hidden) : son API ou ses boutons, sinon un glisser du doigt par l'appli
    // (réponse { drag }). Curseur maintenu contre le bord : une carte par demi-seconde, pas à chaque répétition.
    var car = sliderAt(fx * window.innerWidth, fy * window.innerHeight);
    if (!car) return false;
    var now = Date.now();
    if (now - lastForce < FORCE_MS) return true;
    lastForce = now;
    if (advance(car, px > 0)) return true;
    var br = car.box.getBoundingClientRect();
    var y = Math.max(br.top + 4, Math.min(br.bottom - 4, fy * window.innerHeight));
    var len = Math.min(br.width * 0.6, 360);
    var x0 = br.left + br.width / 2 + (px > 0 ? len / 2 : -len / 2);
    var iw = window.innerWidth, ih = window.innerHeight;
    return JSON.stringify({ drag: [x0 / iw, y / ih, (px > 0 ? x0 - len : x0 + len) / iw, y / ih] });
  };
  var lastForce = 0;
  var FORCE_MS = 450;

  // Le carrousel à transform sous (x, y), même sans carte reconnue : un ancêtre en overflow-x:hidden de la taille d'une
  // rangée (pas toute la page), connu d'une bibliothèque ou dont la piste est déplacée par transform. Jamais un simple
  // conteneur qui déborde (en-tête qui cache un menu) : on y cliquerait un bouton au hasard. null sinon.
  function sliderAt(x, y) {
    var ih = window.innerHeight;
    var el = document.elementFromPoint(x, y);
    for (var a = el, depth = 0; a && !isPage(a) && depth < 25; a = a.parentElement, depth++) {
      var cs = window.getComputedStyle(a);
      if (cs.overflowX !== 'hidden' || cs.display === 'inline') continue;
      var r = a.getBoundingClientRect();
      if (r.height > ih * 0.9 || r.width < 200) continue;
      var root = sliderRoot(a);
      var first = a.firstElementChild;
      var moved = first && window.getComputedStyle(first).transform !== 'none';
      if (root || moved) return { box: a, root: root || a.parentElement || a, scroll: false, drag: true };
    }
    return null;
  }


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
    window.addEventListener('scroll', function () { cardsMemo = null; }, { passive: true });
    new MutationObserver(function () {
      cardsMemo = null;
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
      // Passage au lecteur léger : la page se tait, se fige et cache sa vidéo (le lecteur prend la suite).
      case 'hold': v.muted = true; v.pause(); conceal(true); break;
      // Le lecteur léger n'a pas pu lire ce flux, ou ce n'est pas un film : la page reprend, visible et avec le son.
      case 'release': conceal(false); handoff = false; v.muted = false; if (c.play && v.paused) v.play(); break;
      default: break;
    }
    return state(v);
  }

  // Armé par l'app quand le flux de la page partira dans le lecteur léger : la vidéo qui démarre ne se montre
  // ni ne s'entend dans la page (sinon on la voit jouer puis basculer, comme un à-coup).
  var handoff = false;
  function conceal(on) {
    var old = document.getElementById('__wido_conceal');
    if (old) old.remove();
    if (!on) return;
    var st = document.createElement('style');
    st.id = '__wido_conceal';
    st.textContent = 'video{opacity:0!important}';
    (document.head || document.documentElement).appendChild(st);
  }
  function arm(on) {
    handoff = !!on;
    if (!on) conceal(false);
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
      if (m.cmd === 'arm') { arm(m.on); return null; }
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
        if (m.cmd === 'arm') { arm(m.on); return; }
        var s = command(m);
        if (s) bridge.postMessage(JSON.stringify({ type: 'media', s: s, top: isTop }));
      } catch (e) { /* message illisible : ignoré */ }
    };
    bridge.postMessage(JSON.stringify({ type: 'hello', top: isTop }));
    // Lecture ou pause décidée par la page (bouton du lecteur) : l'app le sait aussitôt (écran de pause).
    // 'durationchange' / 'loadedmetadata' : la durée d'un flux arrive souvent après 'play' ; l'app décide dès qu'elle la connaît.
    ['play', 'playing', 'pause', 'ended', 'durationchange', 'loadedmetadata'].forEach(function (name) {
      document.addEventListener(name, function (e) {
        var v = e.target;
        if (!v || v.tagName !== 'VIDEO' || v.clientWidth * v.clientHeight < 40000) return;
        if (handoff && (name === 'play' || name === 'playing')) { v.muted = true; conceal(true); }
        try { bridge.postMessage(JSON.stringify({ type: 'event', s: state(v), top: isTop })); } catch (err) { /* ignoré */ }
      }, true);
    });
  }
})();
