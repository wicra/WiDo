// Heuristique : retire les calques plein écran (fixed, z-index élevé) qui ne contiennent pas de lecteur.
// À activer site par site : elle peut se tromper sur des pages atypiques.
(function () {
  if (window.__widoOverlays) return;
  window.__widoOverlays = true;
  function suspicious(el) {
    if (!(el instanceof HTMLElement)) return false;
    if (el.querySelector('video, audio, iframe')) return false;
    var cs = getComputedStyle(el);
    if (cs.position !== 'fixed') return false;
    if ((parseInt(cs.zIndex, 10) || 0) < 1000) return false;
    var r = el.getBoundingClientRect();
    return r.width >= innerWidth * 0.8 && r.height >= innerHeight * 0.8;
  }
  function sweep() {
    document.querySelectorAll('body *').forEach(function (el) {
      if (suspicious(el)) el.remove();
    });
    document.documentElement.style.overflow = '';
    if (document.body) document.body.style.overflow = '';
  }
  new MutationObserver(function () { sweep(); }).observe(document.documentElement, { childList: true, subtree: true });
  sweep();
})();
