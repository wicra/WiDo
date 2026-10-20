// Neutralise les fenêtres et onglets ouverts par la page (pop-ups, pop-unders).
// Injecté en tout début de document, dans toutes les frames : la page ne peut pas garder l'original.
(function () {
  if (window.__widoPopups) return;
  window.__widoPopups = true;
  var noop = function () { return null; };
  try { Object.defineProperty(window, 'open', { value: noop, writable: false, configurable: false }); } catch (e) { window.open = noop; }
  // Une iframe vide fournirait un window.open intact : on le neutralise aussi dans les frames créées.
  try {
    var desc = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentWindow');
    Object.defineProperty(HTMLIFrameElement.prototype, 'contentWindow', {
      configurable: true,
      get: function () {
        var w = desc.get.call(this);
        try { if (w && w.open !== noop) Object.defineProperty(w, 'open', { value: noop, writable: false }); } catch (e) {}
        return w;
      },
    });
  } catch (e) {}
  // Liens et formulaires vers une nouvelle fenêtre : ils restent dans la fenêtre, la politique de navigation tranche.
  function sameWindow(el) { if (el && el.target && el.target !== '_self') el.target = '_self'; }
  document.addEventListener('click', function (e) {
    sameWindow(e.target && e.target.closest ? e.target.closest('a[target],area[target]') : null);
  }, true);
  document.addEventListener('submit', function (e) { sameWindow(e.target); }, true);
  // Liens créés puis cliqués par script (sans être dans la page).
  var click = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () { sameWindow(this); return click.apply(this, arguments); };
  var dispatch = EventTarget.prototype.dispatchEvent;
  EventTarget.prototype.dispatchEvent = function (ev) {
    if (ev && ev.type === 'click' && this instanceof HTMLAnchorElement) sameWindow(this);
    return dispatch.apply(this, arguments);
  };
})();
