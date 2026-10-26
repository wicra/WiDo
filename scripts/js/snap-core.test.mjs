// Tests de la logique du curseur aimanté. Lancer : node scripts/js/snap-core.test.mjs
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const { pickTarget, pickPrimary, pickModal, pickInRow, revealDelta, navDir } = createRequire(import.meta.url)('../../app/src/main/assets/internal/snap-core.js');

// Grille 3 x 3 de boutons 100x40, pas de 200 x 100 ; le curseur est sur le bouton central (400, 300).
const grid = [];
for (let row = 0; row < 3; row++) for (let col = 0; col < 3; col++) grid.push({ x: 200 + col * 200, y: 200 + row * 100, w: 100, h: 40 });
const at = (col, row) => row * 3 + col;
const center = grid[at(1, 1)];

// Carrousel : une rangée de 8 affiches 160x240 au pas de 300 (x de 100 à 2200 : les dernières hors écran, au-delà de 1920),
// et deux affiches dans la rangée du dessous.
const row = [];
for (let i = 0; i < 8; i++) row.push({ x: 100 + i * 300, y: 300, w: 160, h: 240 });
const below = [{ x: 400, y: 600, w: 160, h: 240 }, { x: 2400, y: 600, w: 160, h: 240 }];
const rowAndBelow = row.concat(below);

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
  // Carrousels
  'rangée : ▶ va à l’affiche suivante, même hors écran': () => assert.equal(pickInRow('right', 1900, 300, row, 180, 420), 7),
  'rangée : ▶ prend la plus proche, pas une plus lointaine': () => assert.equal(pickInRow('right', 100, 300, row, 180, 420), 1),
  'rangée : ◀ revient en arrière': () => assert.equal(pickInRow('left', 700, 300, row, 180, 420), 1),
  'rangée : au bout, rien (pas de saut en diagonale vers la rangée du dessous)': () => assert.equal(pickInRow('right', 2200, 300, rowAndBelow, 180, 420), -1),
  'sans filtre de rangée, pickTarget saute en diagonale': () => assert.equal(pickTarget('right', 2200, 300, rowAndBelow), 9),
  'rangée : index de la liste complète': () => assert.equal(pickInRow('left', 2400, 600, rowAndBelow, 480, 720), 8),
  'révéler : déjà visible, 0': () => assert.equal(revealDelta(100, 260, 0, 1920, 48), 0),
  'révéler : dépasse à droite, défile du surplus + marge': () => assert.equal(revealDelta(1820, 1980, 0, 1920, 48), 1980 - (1920 - 48)),
  'révéler : dépasse à gauche, valeur négative': () => assert.equal(revealDelta(-100, 60, 0, 1920, 48), -100 - 48),
  'révéler : marge réduite si la place manque': () => assert.equal(revealDelta(-10, 90, 0, 120, 48), -10 - 10),
  'révéler : plus grand que la zone, aligné au début': () => assert.equal(revealDelta(50, 2000, 0, 1080, 100), 50),
  'bouton : Swiper suivant': () => assert.equal(navDir('swiper-button-next'), 1),
  'bouton : Slick précédent': () => assert.equal(navDir('slick-prev slick-arrow'), -1),
  'bouton : Flickity (prev-next-button next)': () => assert.equal(navDir('flickity-button flickity-prev-next-button next'), 1),
  'bouton : Glide flèche droite': () => assert.equal(navDir('glide__arrow glide__arrow--right'), 1),
  'bouton : aria-label français': () => assert.equal(navDir('Diapositive précédente'), -1),
  'bouton : chevron ›': () => assert.equal(navDir('›'), 1),
  'bouton : camelCase arrowRight': () => assert.equal(navDir('ArrowRight icon'), 1),
  'bouton : « copyright » n’est pas « right »': () => assert.equal(navDir('copyright'), 0),
  'bouton : rien de reconnu, 0': () => assert.equal(navDir('carousel-button'), 0),
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
