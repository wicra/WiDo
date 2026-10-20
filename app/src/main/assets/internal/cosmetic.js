// Masquage cosmétique (règles `##` des listes) : WiDo appelle run(plan) au tout début de chaque
// document. La feuille de style est construite pour l'hôte réellement affiché, dans la page
// principale seulement. Même logique que CosmeticPlan.selectorsFor (module core).
function run(P) {
  if (window.top !== window || window.__widoCosmetic) return;
  window.__widoCosmetic = true;
  var host = location.hostname.toLowerCase();
  function matches(d) { return d === '' || host === d || host.slice(-d.length - 1) === '.' + d; }
  function any(list) { for (var i = 0; i < list.length; i++) if (matches(list[i])) return true; return false; }
  var off = P.off || [], ng = P.ng || [], g = P.g || [], d = P.d || {}, u = P.u || {};
  if (any(off)) return;
  var unhide = {};
  Object.keys(u).forEach(function (k) { if (matches(k)) u[k].forEach(function (s) { unhide[s] = 1; }); });
  var picked = [];
  if (!any(ng)) for (var i = 0; i < g.length; i++) if (!unhide[g[i]]) picked.push(g[i]);
  Object.keys(d).forEach(function (k) { if (matches(k)) d[k].forEach(function (s) { if (!unhide[s]) picked.push(s); }); });
  if (!picked.length) return;
  // Un sélecteur invalide annulerait toute sa règle CSS : on ne garde que ceux que le navigateur comprend.
  var probe = document.createDocumentFragment();
  var valid = picked.filter(function (s) { try { probe.querySelector(s); return true; } catch (e) { return false; } });
  var css = '';
  for (var j = 0; j < valid.length; j += 400) css += valid.slice(j, j + 400).join(',') + '{display:none!important}\n';
  function add() {
    if (document.getElementById('__wido_cosmetic')) return;
    var s = document.createElement('style');
    s.id = '__wido_cosmetic';
    s.textContent = css;
    (document.head || document.documentElement).appendChild(s);
  }
  if (document.documentElement) add(); else document.addEventListener('readystatechange', add, { once: true });
}
