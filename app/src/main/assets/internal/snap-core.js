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

  if (typeof module !== 'undefined' && module.exports) module.exports = { pickTarget: pickTarget };
  else root.__widoPick = pickTarget;
})(typeof window !== 'undefined' ? window : this);
