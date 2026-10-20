// Champs de saisie des pages : signale à WiDo quand un champ texte reçoit ou perd le focus (WiDo
// affiche alors un QR code pour taper depuis le téléphone), et écrit le texte reçu du téléphone.
// Injecté dans toutes les frames : le champ peut être dans une iframe.
(function () {
  if (window.__widoFields) return;
  window.__widoFields = true;
  var TEXT_TYPES = /^(text|search|email|url|tel|password|number|)$/;
  function editable(e) {
    if (!e) return false;
    if (e.isContentEditable) return true;
    if (e.tagName === 'TEXTAREA') return !e.disabled && !e.readOnly;
    if (e.tagName === 'INPUT') return TEXT_TYPES.test((e.getAttribute('type') || '').toLowerCase()) && !e.disabled && !e.readOnly;
    return false;
  }
  function post(on) {
    var bridge = window.WiDoBridge;
    if (bridge && typeof bridge.postMessage === 'function') bridge.postMessage(JSON.stringify({ type: 'field', on: on }));
  }
  document.addEventListener('focusin', function (e) { if (editable(e.target)) post(true); }, true);
  document.addEventListener('focusout', function (e) { if (editable(e.target)) post(false); }, true);

  // Écrit `t` comme une frappe (événements input/change), puis valide si `submit`. Seule la frame qui
  // a le champ actif écrit ; la page principale se rabat sur le premier champ visible.
  window.__widoType = function (t, submit) {
    var el = document.activeElement;
    if (!editable(el)) {
      if (window.top !== window || (el && el.tagName === 'IFRAME')) return false;
      el = Array.prototype.find.call(
        document.querySelectorAll('input[type=search],input[type=text],input:not([type]),textarea'),
        function (e) { var r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && !e.disabled; });
    }
    if (!el) return false;
    el.focus();
    if (el.isContentEditable) {
      document.execCommand('selectAll', false, null);
      document.execCommand('insertText', false, t);
    } else {
      var setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value').set;
      setter.call(el, t);
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    }
    if (submit) {
      ['keydown', 'keypress', 'keyup'].forEach(function (type) {
        el.dispatchEvent(new KeyboardEvent(type, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
      });
      if (el.form) { if (el.form.requestSubmit) el.form.requestSubmit(); else el.form.submit(); }
    }
    return true;
  };
})();
