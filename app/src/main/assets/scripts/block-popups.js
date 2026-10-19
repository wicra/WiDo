// Neutralise les ouvertures de fenêtres (pop-ups) déclenchées par la page.
(function () {
  if (window.__widoPopups) return;
  window.__widoPopups = true;
  window.open = function () { return null; };
  document.addEventListener('click', function (e) {
    var el = e.target && e.target.closest ? e.target.closest('a[target]') : null;
    if (el && el.target !== '_self') el.target = '_self'; // reste dans la fenêtre, la politique de navigation tranche
  }, true);
})();
