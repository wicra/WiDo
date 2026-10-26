// Choix de la cible du curseur « aimanté » : logique pure, testée sous Node (scripts/js/snap-core.test.mjs).
(function (root) {
  /**
   * dir : 'left' | 'right' | 'up' | 'down'.
   * (cx, cy) : position actuelle du curseur. items : [{x, y, w, h}] centres et tailles des éléments cliquables.
   * Renvoie l'index de l'élément le plus naturel dans cette direction, ou -1.
   */
  function pickTarget(dir, cx, cy, items) {
    var best = -1;
    var bestScore = Infinity;
    for (var i = 0; i < items.length; i++) {
      var it = items[i];
      var dx = it.x - cx;
      var dy = it.y - cy;
      var primary, secondary, half;
      switch (dir) {
        case 'left': primary = -dx; secondary = Math.abs(dy); half = it.h / 2; break;
        case 'right': primary = dx; secondary = Math.abs(dy); half = it.h / 2; break;
        case 'up': primary = -dy; secondary = Math.abs(dx); half = it.w / 2; break;
        case 'down': primary = dy; secondary = Math.abs(dx); half = it.w / 2; break;
        default: return -1;
      }
      if (primary < 6) continue; // pas dans cette direction
      if (secondary - half > primary * 1.2) continue; // hors du cône d'environ 50 degrés
      var score = primary + secondary * 3; // l'alignement compte nettement plus que la distance
      if (score < bestScore) {
        bestScore = score;
        best = i;
      }
    }
    return best;
  }

  /**
   * Quel bouton d'une fenêtre surgissante (cookies, pop-up) le curseur doit-il rejoindre ? `labels` : les textes
   * des boutons, dans l'ordre de la page. « Tout accepter » > « Accepter / OK / Continuer » > « Fermer » ; à défaut,
   * le premier. Renvoie l'index, ou -1 s'il n'y a aucun bouton.
   */
  function pickPrimary(labels) {
    var best = -1;
    var bestScore = 0;
    for (var i = 0; i < labels.length; i++) {
      var t = String(labels[i] || '').trim().toLowerCase();
      var score = 0;
      if (/tout accepter|accept all|allow all|accepter et fermer|j['’]accepte|i agree|agree and/.test(t)) score = 3;
      else if (/accepter|accept|autoriser|allow|\bok\b|okay|got it|compris|d['’]accord|continuer|continue/.test(t)) score = 2;
      else if (/fermer|close|no thanks|non merci|^[x×✕✖]$/.test(t)) score = 1;
      if (score > bestScore) {
        bestScore = score;
        best = i;
      }
    }
    if (best >= 0) return best;
    return labels.length ? 0 : -1;
  }

  /**
   * Quelle fenêtre surgissante est au premier plan ? `cands` : [{z, order}] (z-index, rang dans la page).
   * La plus haute (z-index), à égalité la dernière dans la page. Renvoie l'index, ou -1.
   */
  function pickModal(cands) {
    var best = -1;
    for (var i = 0; i < cands.length; i++) {
      if (best < 0 || cands[i].z > cands[best].z || (cands[i].z === cands[best].z && cands[i].order > cands[best].order)) best = i;
    }
    return best;
  }

  /**
   * Comme pickTarget, mais seulement parmi les éléments de la même rangée : centre vertical dans [top, bottom].
   * Sert dans un carrousel, où `items` contient aussi les éléments rognés ou hors écran (positions réelles).
   */
  function pickInRow(dir, cx, cy, items, top, bottom) {
    var idx = [];
    var sub = [];
    for (var i = 0; i < items.length; i++) {
      if (items[i].y >= top && items[i].y <= bottom) { idx.push(i); sub.push(items[i]); }
    }
    var j = pickTarget(dir, cx, cy, sub);
    return j < 0 ? -1 : idx[j];
  }

  /**
   * De combien défiler (signé, en px) pour que [start, end] tienne dans la zone visible [vs, ve], avec une
   * marge `pad` de chaque côté si la place le permet. 0 si c'est déjà le cas ; trop grand : aligné au début.
   */
  function revealDelta(start, end, vs, ve, pad) {
    var room = ve - vs;
    var size = end - start;
    if (size >= room) return start - vs;
    var p = Math.max(0, Math.min(pad, (room - size) / 2));
    if (start < vs + p) return start - (vs + p);
    if (end > ve - p) return end - (ve - p);
    return 0;
  }

  /**
   * Sens d'un bouton de carrousel d'après sa classe, son aria-label, son titre ou son texte :
   * 1 = suivant (vers la droite), -1 = précédent, 0 = inconnu ou ambigu.
   */
  function navDir(text) {
    var t = ' ' + String(text || '').toLowerCase().replace(/prev-next|previous-next/g, ' ') + ' ';
    var next = /next|suivant|forward|droite|[^a-z]right|arrow-?right|chevron-?right|›|»|→|❯|>/.test(t);
    var prev = /prev|pr[ée]c[ée]dent|gauche|[^a-z]left|arrow-?left|chevron-?left|[^a-z]back[^a-z]|‹|«|←|❮|</.test(t);
    return next === prev ? 0 : (next ? 1 : -1);
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = {
    pickTarget: pickTarget, pickPrimary: pickPrimary, pickModal: pickModal,
    pickInRow: pickInRow, revealDelta: revealDelta, navDir: navDir
  };
  else {
    root.__widoPick = pickTarget;
    root.__widoPickPrimary = pickPrimary;
    root.__widoPickModal = pickModal;
    root.__widoPickInRow = pickInRow;
    root.__widoRevealDelta = revealDelta;
    root.__widoNavDir = navDir;
  }
})(typeof window !== 'undefined' ? window : this);
