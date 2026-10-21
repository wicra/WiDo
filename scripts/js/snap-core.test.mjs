// Tests de la logique du curseur aimanté. Lancer : node scripts/js/snap-core.test.mjs
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const { pickTarget, pickPrimary, pickModal } = createRequire(import.meta.url)('../../app/src/main/assets/internal/snap-core.js');

// Grille 3 x 3 de boutons 100x40, pas de 200 x 100 ; le curseur est sur le bouton central (400, 300).
const grid = [];
for (let row = 0; row < 3; row++) for (let col = 0; col < 3; col++) grid.push({ x: 200 + col * 200, y: 200 + row * 100, w: 100, h: 40 });
const at = (col, row) => row * 3 + col;
const center = grid[at(1, 1)];

const cases = {
  'à droite : le voisin de droite': () => assert.equal(pickTarget('right', center.x, center.y, grid), at(2, 1)),
  'à gauche : le voisin de gauche': () => assert.equal(pickTarget('left', center.x, center.y, grid), at(0, 1)),
  'en haut : le voisin du dessus': () => assert.equal(pickTarget('up', center.x, center.y, grid), at(1, 0)),
  'en bas : le voisin du dessous': () => assert.equal(pickTarget('down', center.x, center.y, grid), at(1, 2)),
  'depuis un coin, rien au-delà du bord': () => assert.equal(pickTarget('right', 600, 300, grid.filter((g) => g.x <= 600)), -1),
  'ignore ce qui est derrière': () => assert.equal(pickTarget('right', 600, 300, [{ x: 200, y: 300, w: 100, h: 40 }]), -1),
  'ignore un élément trop décalé sur le côté': () => assert.equal(pickTarget('right', 100, 100, [{ x: 160, y: 900, w: 100, h: 40 }]), -1),
  "préfère l'aligné à un plus proche mais décalé": () => {
    const items = [{ x: 300, y: 380, w: 100, h: 40 }, { x: 500, y: 300, w: 100, h: 40 }];
    assert.equal(pickTarget('right', 100, 300, items), 1);
  },
  'ignore ce qui est sous le curseur (même position)': () => assert.equal(pickTarget('right', center.x, center.y, [center]), -1),
  'direction inconnue : -1': () => assert.equal(pickTarget('diagonale', 0, 0, grid), -1),
  'liste vide : -1': () => assert.equal(pickTarget('down', 0, 0, []), -1),
  // Fenêtres surgissantes (cookies, pop-up)
  'cookies : « Tout accepter » avant « Personnaliser »': () => assert.equal(pickPrimary(['Personnaliser', 'Tout refuser', 'Tout accepter']), 2),
  'cookies en anglais': () => assert.equal(pickPrimary(['Manage', 'Accept all']), 1),
  'accepter avant fermer': () => assert.equal(pickPrimary(['Fermer', 'Accepter']), 1),
  'seulement fermer : fermer': () => assert.equal(pickPrimary(['Réglages', '×']), 1),
  'aucun mot reconnu : le premier': () => assert.equal(pickPrimary(['Abonnez-vous', 'Plus tard']), 0),
  'aucun bouton : -1': () => assert.equal(pickPrimary([]), -1),
  'la fenêtre la plus haute gagne': () => assert.equal(pickModal([{ z: 10, order: 5 }, { z: 9999, order: 1 }, { z: 50, order: 9 }]), 1),
  'à z égal, la dernière de la page': () => assert.equal(pickModal([{ z: 0, order: 3 }, { z: 0, order: 7 }, { z: 0, order: 5 }]), 1),
  'aucune fenêtre : -1': () => assert.equal(pickModal([]), -1),
};

let failed = 0;
for (const [name, run] of Object.entries(cases)) {
  try { run(); console.log('ok   ', name); } catch (e) { failed++; console.log('ECHEC', name, '\n     ', e.message); }
}
process.exit(failed ? 1 : 0);
