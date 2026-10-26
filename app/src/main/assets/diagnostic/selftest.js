// Test complet de WiDo (Réglages → Test) : exerce les scripts de WiDo injectés dans la page de test
// (curseur aimanté, carrousels, clavier du téléphone, passage au lecteur léger, fenêtres de cookies).
// Résultat dans window.__widoSelfTest : { done, cases: [{name, ok, detail}], metrics }.
(function () {
  'use strict';
  var R = window.__widoSelfTest = { done: false, cases: [], metrics: {} };
  var iw = window.innerWidth, ih = window.innerHeight;
  var times = [];
  function ok(name, pass, detail) { R.cases.push({ name: name, ok: !!pass, detail: detail || '' }); }
  function $(id) { return document.getElementById(id); }
  function center(el) { var r = el.getBoundingClientRect(); return [(r.left + r.width / 2) / iw, (r.top + r.height / 2) / ih]; }
  function snap(dir, p) {
    var t0 = performance.now();
    var raw = window.__widoSnap ? window.__widoSnap(dir, p[0], p[1]) : null;
    times.push(performance.now() - t0);
    try { return raw ? JSON.parse(raw) : null; } catch (e) { return null; }
  }
  function settle(cb, tries) {
    setTimeout(function () {
      var raw = window.__widoSnapSettle ? window.__widoSnapSettle(tries <= 1) : null;
      var o = null; try { o = raw ? JSON.parse(raw) : null; } catch (e) {}
      if (o && !('x' in o) && o.wait && tries > 1) return settle(cb, tries - 1);
      cb(o);
    }, 150);
  }
  function at(p) { return document.elementFromPoint(p[0] * iw, p[1] * ih); }
  function visibleIn(el, box) {
    var r = el.getBoundingClientRect(), b = box.getBoundingClientRect();
    return r.left >= b.left - 2 && r.right <= b.right + 2 && r.top >= 0 && r.bottom <= ih;
  }

  var steps = [];

  // 1. Curseur aimanté : ▶ va au lien voisin.
  steps.push(function (next) {
    var r = snap('right', center($('g0')));
    var want = center($('g1'));
    ok('Curseur : ▶ va au lien voisin', r && Math.abs(r.x - want[0]) < 0.02 && Math.abs(r.y - want[1]) < 0.02,
      r ? 'arrivé en ' + r.x.toFixed(3) + ',' + r.y.toFixed(3) : 'aucune cible');
    next();
  });

  // 2. ▼ descend sur la rangée suivante et la montre en entier.
  steps.push(function (next) {
    var start = center($('g0'));
    var r = snap('down', start);
    var el = r ? at([r.x, r.y]) : null;
    var r2 = el ? el.getBoundingClientRect() : null;
    ok('Curseur : ▼ descend et montre la cible en entier', r && r.y > start[1] && r2 && r2.top >= 0 && r2.bottom <= ih,
      el ? 'sur « ' + (el.textContent || el.tagName).trim().slice(0, 30) + ' »' : 'aucune cible');
    next();
  });

  // 3. Rangée défilante : ▶ jusqu'au bout fait défiler la rangée, le curseur reste sur une affiche visible, puis s'arrête.
  steps.push(function (next) {
    var row = $('row');
    row.scrollLeft = 0;
    $('c0').scrollIntoView({ block: 'center' });
    var p = center($('c0')), edge = false, moves = 0, hidden = 0;
    for (var i = 0; i < 20; i++) {
      var r = snap('right', p);
      if (!r) break;
      if (r.edge) { edge = true; break; }
      p = [r.x, r.y]; moves++;
      var el = at(p);
      var card = el && el.closest ? el.closest('#row a') : null;
      if (!card || !visibleIn(card, row)) hidden++;
    }
    ok('Carrousel défilant : ▶ fait défiler la rangée', row.scrollLeft > 0, 'défilement ' + Math.round(row.scrollLeft) + ' px, ' + moves + ' pas');
    ok('Carrousel défilant : le curseur reste sur une affiche visible', moves > 0 && hidden === 0, hidden + ' arrêt(s) sur une affiche cachée');
    ok('Carrousel défilant : arrêt au bout de la rangée', edge, edge ? '' : 'pas de bout détecté en 20 pas');
    next();
  });

  // 4. Carrousel animé (transform + boutons) : ▶ avance la piste jusqu'au bout.
  steps.push(function (next) {
    var track = $('swt');
    $('sw').scrollIntoView({ block: 'center' });
    var p = center($('s0')), i = 0, drag = 0, advanced = 0, edge = false;
    function one() {
      if (i++ >= 20) return done();
      var before = track.style.transform;
      var r = snap('right', p);
      if (!r) return done();
      if (r.edge) { edge = true; return done(); }
      if (r.drag) drag++;
      if (r.wait || r.drag) {
        settle(function (o) {
          if (track.style.transform !== before) advanced++;
          if (o && 'x' in o) p = [o.x, o.y];
          one();
        }, 6);
        return;
      }
      if (track.style.transform !== before) advanced++;
      p = [r.x, r.y];
      one();
    }
    function done() {
      ok('Carrousel animé : ▶ fait avancer la piste', advanced > 0 || /translate/.test(track.style.transform),
        'piste ' + (track.style.transform || 'immobile') + (drag ? ', ' + drag + ' glisser(s) demandé(s)' : ''));
      ok('Carrousel animé : arrêt au bout', edge, edge ? '' : 'pas de bout détecté');
      next();
    }
    one();
  });

  // 4 bis. Affiches sans lien (<div> au curseur « main ») : le curseur les vise, ▶ fait défiler la rangée jusqu'au bout.
  steps.push(function (next) {
    var row = $('cards');
    row.scrollLeft = 0;
    row.scrollIntoView({ block: 'center' });
    var first = center($('k0'));
    var r = snap('down', [first[0], Math.max(0, first[1] - 0.2)]);
    var el = r ? at([r.x, r.y]) : null;
    var card = el && el.closest ? el.closest('#cards > div') : null;
    ok('Cartes sans lien : le curseur vise une affiche', !!card, card ? 'sur « ' + card.textContent + ' »' : (el ? 'sur ' + el.tagName : 'aucune cible'));
    var p = center($('k0')), edge = false, moves = 0;
    for (var i = 0; i < 20; i++) {
      var q = snap('right', p);
      if (!q) break;
      if (q.edge) { edge = true; break; }
      p = [q.x, q.y]; moves++;
    }
    var last = at(p);
    var lastCard = last && last.closest ? last.closest('#cards > div') : null;
    ok('Cartes sans lien : ▶ parcourt la rangée jusqu\'au bout', edge && lastCard && lastCard.id === 'k11',
      moves + ' pas, défilement ' + Math.round(row.scrollLeft) + ' px, ' + (lastCard ? 'sur ' + lastCard.id : 'hors affiche'));
    next();
  });

  // 4 ter. « Épisode suivant » de la page (proposé à la fin du film) : trouvé, sans confondre avec « Suivant » du carrousel.
  steps.push(function (next) {
    window.__widoNextClicked = false;
    var label = window.__widoNextEpisode ? window.__widoNextEpisode(false) : null;
    if (window.__widoNextEpisode) window.__widoNextEpisode(true);
    ok('Épisode suivant : trouvé et suivi', label === 'Épisode suivant' && window.__widoNextClicked, 'trouvé « ' + label + ' »');
    next();
  });

  // 5. Clavier du téléphone : le texte entier à chaque frappe (accents compris), Entrée valide ce qui est tapé.
  steps.push(function (next) {
    var q = $('q');
    q.value = ''; q.focus(); window.__widoSubmitted = null;
    var ok1 = window.__widoType && window.__widoType('é', false) && window.__widoType('éà', false) && window.__widoType('éàç ok', false);
    var typed = q.value;
    if (window.__widoType) window.__widoType('éàç ok', true);
    ok('Clavier du téléphone : texte entier, accents', ok1 && typed === 'éàç ok', 'champ = « ' + typed + ' »');
    ok('Clavier du téléphone : Entrée envoie le texte tapé', window.__widoSubmitted === 'éàç ok', 'envoyé = « ' + window.__widoSubmitted + ' »');
    next();
  });

  // 6. Passage au lecteur léger : page armée, la vidéo qui démarre est muette et cachée ; puis rendue à la page.
  steps.push(function (next) {
    var v = $('vid');
    var bridge = window.WiDoBridge;
    var hasHandler = bridge && typeof bridge.onmessage === 'function';
    if (!hasHandler) { ok('Lecteur léger : la page se cache au démarrage du film', false, 'passerelle absente'); return next(); }
    v.muted = false;
    bridge.onmessage({ data: JSON.stringify({ cmd: 'arm', on: true }) });
    v.dispatchEvent(new Event('play'));
    var hidden = !!document.getElementById('__wido_conceal') && v.muted;
    bridge.onmessage({ data: JSON.stringify({ cmd: 'release', play: false }) });
    var back = !document.getElementById('__wido_conceal') && !v.muted;
    ok('Lecteur léger : la vidéo de la page se cache et se tait au démarrage', hidden, hidden ? '' : 'vidéo encore visible ou sonore');
    ok('Lecteur léger : la page reprend sa vidéo si besoin', back, back ? '' : 'vidéo restée cachée');
    next();
  });

  // 7. Fenêtre de cookies : repérée, et le curseur est attiré sur « Tout accepter ».
  steps.push(function (next) {
    window.__widoMsgs.length = 0;
    var m = document.createElement('div');
    m.id = 'cookie-banner';
    m.setAttribute('role', 'dialog');
    m.style.cssText = 'position:fixed;left:20%;top:30%;width:60%;height:200px;background:#fff;color:#000;z-index:99';
    m.innerHTML = '<p>Ce site utilise des cookies.</p><button id="cfg">Personnaliser</button> <button id="acc">Tout accepter</button>';
    document.body.appendChild(m);
    setTimeout(function () {
      var msg = null;
      window.__widoMsgs.forEach(function (s) { try { var o = JSON.parse(s); if (o.type === 'modal' && !o.gone) msg = o; } catch (e) {} });
      var want = center($('acc'));
      ok('Cookies : fenêtre repérée', !!msg, msg ? '' : 'aucun message de fenêtre');
      ok('Cookies : curseur sur « Tout accepter »', msg && Math.abs(msg.x - want[0]) < 0.03 && Math.abs(msg.y - want[1]) < 0.03,
        msg ? 'visé ' + msg.x.toFixed(3) + ',' + msg.y.toFixed(3) : '');
      m.remove();
      next();
    }, 1600);
  });

  function run(i) {
    if (i >= steps.length) {
      var sum = 0, max = 0;
      times.forEach(function (t) { sum += t; if (t > max) max = t; });
      R.metrics.snapCalls = times.length;
      R.metrics.snapAvgMs = times.length ? Math.round(sum / times.length * 10) / 10 : 0;
      R.metrics.snapMaxMs = Math.round(max * 10) / 10;
      R.metrics.links = document.querySelectorAll('a[href],button').length;
      R.done = true;
      return;
    }
    try { steps[i](function () { setTimeout(function () { run(i + 1); }, 30); }); }
    catch (e) { ok('Étape ' + (i + 1) + ' : erreur', false, String(e && e.message || e)); run(i + 1); }
  }
  setTimeout(function () { run(0); }, 50);
})();
