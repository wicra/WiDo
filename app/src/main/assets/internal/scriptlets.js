// Scriptlets des listes uBlock (`site.com##+js(nom, arg1, arg2)`) : de petits correctifs lancés avant
// les scripts de la page, qui désarment pubs et détecteurs de bloqueur là où bloquer des requêtes ne
// suffit pas. WiDo appelle run(plan) au début de chaque document, dans chaque frame, et ne garde que
// les scriptlets du domaine affiché. Même logique de choix que CosmeticPlan.scriptsFor (module core).
// Seuls les scriptlets les plus utilisés des listes sont implémentés ; les autres sont ignorés.
function run(P) {
  if (window.__widoScriptlets) return;
  window.__widoScriptlets = true;
  var host = location.hostname.toLowerCase();
  function matches(d) {
    if (d === '') return true;
    if (d.slice(-2) === '.*') {
      var base = d.slice(0, -2), labels = host.split('.');
      for (var cut = 1; cut <= 2; cut++) {
        if (labels.length <= cut) continue;
        var rest = labels.slice(0, labels.length - cut).join('.');
        if (rest === base || rest.slice(-base.length - 1) === '.' + base) return true;
      }
      return false;
    }
    return host === d || host.slice(-d.length - 1) === '.' + d;
  }
  var scripts = P.j || {}, unscripts = P.uj || {}, disabled = {}, calls = [];
  Object.keys(unscripts).forEach(function (d) { if (matches(d)) unscripts[d].forEach(function (s) { disabled[s] = 1; }); });
  if (disabled['']) return;
  Object.keys(scripts).forEach(function (d) {
    if (matches(d)) scripts[d].forEach(function (s) { if (!disabled[s] && calls.indexOf(s) < 0) calls.push(s); });
  });
  if (!calls.length) return;

  // --- Outils communs --------------------------------------------------------------------------
  var MAGIC = 'WiDo-scriptlet-' + Math.random().toString(36).slice(2);
  window.addEventListener('error', function (e) {
    if (e && typeof e.message === 'string' && e.message.indexOf(MAGIC) >= 0) e.preventDefault();
  }, true);
  function abort() { throw new ReferenceError(MAGIC); }

  /** `/regex/i`, `!négation`, texte simple ou vide (tout). Renvoie une fonction de test. */
  function needle(arg) {
    var s = arg === undefined ? '' : String(arg);
    var negate = false;
    if (s.charAt(0) === '!') { negate = true; s = s.slice(1); }
    var test;
    if (s === '' || s === '*') {
      test = function () { return true; };
    } else if (s.length > 2 && s.charAt(0) === '/' && s.lastIndexOf('/') > 0) {
      var last = s.lastIndexOf('/');
      var re;
      try { re = new RegExp(s.slice(1, last), s.slice(last + 1)); } catch (e) { re = null; }
      test = function (v) { return re ? re.test(String(v)) : false; };
    } else {
      test = function (v) { return String(v).indexOf(s) >= 0; };
    }
    return negate ? function (v) { return !test(v); } : test;
  }

  /** Piège une propriété en chaîne (`a.b.c`), même si les objets intermédiaires n'existent pas encore. */
  function trapChain(owner, chain, onFinal) {
    var pos = chain.indexOf('.');
    if (pos < 0) { onFinal(owner, chain); return; }
    var prop = chain.slice(0, pos), rest = chain.slice(pos + 1);
    var v = owner[prop];
    if (v !== null && (typeof v === 'object' || typeof v === 'function')) { trapChain(v, rest, onFinal); return; }
    var desc = Object.getOwnPropertyDescriptor(owner, prop);
    if (desc && desc.set) return;
    try {
      Object.defineProperty(owner, prop, {
        configurable: true,
        get: function () { return v; },
        set: function (a) {
          v = a;
          if (a !== null && (typeof a === 'object' || typeof a === 'function')) trapChain(a, rest, onFinal);
        },
      });
    } catch (e) {}
  }

  function constantValue(raw) {
    switch (raw) {
      case 'undefined': return undefined;
      case 'false': return false;
      case 'true': return true;
      case 'null': return null;
      case "''": case '""': case 'emptyStr': return '';
      case 'noopFunc': return function () {};
      case 'trueFunc': return function () { return true; };
      case 'falseFunc': return function () { return false; };
      case 'throwFunc': return function () { throw new Error(MAGIC); };
      case 'emptyArr': return [];
      case 'emptyObj': return {};
      case 'noopPromiseResolve': return function () { return Promise.resolve(); };
      case 'noopPromiseReject': return function () { return Promise.reject(); };
      case 'yes': return 'yes';
      case 'no': return 'no';
      default:
        if (/^-?\d{1,5}$/.test(raw)) return parseInt(raw, 10);
        return { invalid: true };
    }
  }

  function isOurScript(s) { return s && s.textContent && s.textContent.indexOf('__widoScriptlets') >= 0; }

  function wrapTimer(name, test, delayTest, transform) {
    var original = window[name];
    window[name] = new Proxy(original, {
      apply: function (target, self, args) {
        var fn = args[0], delay = args[1];
        var text = typeof fn === 'function' ? Function.prototype.toString.call(fn) : String(fn);
        if (test(text) && delayTest(delay)) {
          var replaced = transform(args);
          if (replaced === null) return 0;
          args = replaced;
        }
        return Reflect.apply(target, self, args);
      },
    });
  }

  function delayMatcher(arg) {
    if (arg === undefined || arg === '') return function () { return true; };
    var negate = arg.charAt(0) === '!';
    var n = parseInt(negate ? arg.slice(1) : arg, 10);
    return function (d) { var same = (parseInt(d, 10) || 0) === n; return negate ? !same : same; };
  }

  function prunePath(obj, path) {
    var parts = path.split('.');
    (function walk(o, i) {
      if (!o || typeof o !== 'object') return;
      var key = parts[i];
      if (i === parts.length - 1) {
        if (key === '[]') Object.keys(o).forEach(function (k) { delete o[k]; });
        else if (key in o) delete o[key];
        return;
      }
      if (key === '[]') Object.keys(o).forEach(function (k) { walk(o[k], i + 1); });
      else walk(o[key], i + 1);
    })(obj, 0);
  }

  function hasPath(obj, path) {
    var o = obj, parts = path.split('.');
    for (var i = 0; i < parts.length; i++) {
      if (!o || typeof o !== 'object' || !(parts[i] in o)) return false;
      o = o[parts[i]];
    }
    return true;
  }

  function onDom(fn) {
    var observer = new MutationObserver(function (records) {
      for (var i = 0; i < records.length; i++) {
        var added = records[i].addedNodes;
        for (var j = 0; j < added.length; j++) fn(added[j]);
      }
    });
    observer.observe(document, { childList: true, subtree: true });
  }

  // --- Scriptlets ------------------------------------------------------------------------------
  var S = {};

  S['set-constant'] = function (chain, raw) {
    var value = constantValue(raw);
    if (!chain || (value && value.invalid)) return;
    trapChain(window, chain, function (owner, prop) {
      try {
        Object.defineProperty(owner, prop, { configurable: false, get: function () { return value; }, set: function () {} });
      } catch (e) {}
    });
  };

  S['abort-on-property-read'] = function (chain) {
    if (!chain) return;
    trapChain(window, chain, function (owner, prop) {
      try { Object.defineProperty(owner, prop, { configurable: true, get: abort, set: function () {} }); } catch (e) {}
    });
  };

  S['abort-on-property-write'] = function (chain) {
    if (!chain) return;
    trapChain(window, chain, function (owner, prop) {
      try { delete owner[prop]; Object.defineProperty(owner, prop, { configurable: true, set: abort }); } catch (e) {}
    });
  };

  S['abort-current-script'] = function (chain, search) {
    if (!chain) return;
    var test = needle(search);
    trapChain(window, chain, function (owner, prop) {
      var value = owner[prop];
      var desc = Object.getOwnPropertyDescriptor(owner, prop);
      function check() {
        var s = document.currentScript;
        if (s instanceof HTMLScriptElement && !s.src && !isOurScript(s) && test(s.textContent)) abort();
      }
      try {
        Object.defineProperty(owner, prop, {
          configurable: true,
          get: function () { check(); return desc && desc.get ? desc.get.call(owner) : value; },
          set: function (a) { check(); if (desc && desc.set) desc.set.call(owner, a); else value = a; },
        });
      } catch (e) {}
    });
  };

  S['no-setTimeout-if'] = function (search, delay) {
    wrapTimer('setTimeout', needle(search), delayMatcher(delay), function () { return null; });
  };

  S['no-setInterval-if'] = function (search, delay) {
    wrapTimer('setInterval', needle(search), delayMatcher(delay), function () { return null; });
  };

  S['nano-setInterval-booster'] = function (search, delay, boost) {
    var factor = parseFloat(boost);
    if (!(factor >= 0.001 && factor <= 50)) factor = 0.05;
    var expected = delay === undefined || delay === '' ? '1000' : delay;
    wrapTimer('setInterval', needle(search), delayMatcher(expected), function (args) {
      args = Array.prototype.slice.call(args);
      args[1] = (parseInt(args[1], 10) || 0) * factor;
      return args;
    });
  };

  S['nano-setTimeout-booster'] = function (search, delay, boost) {
    var factor = parseFloat(boost);
    if (!(factor >= 0.001 && factor <= 50)) factor = 0.05;
    var expected = delay === undefined || delay === '' ? '1000' : delay;
    wrapTimer('setTimeout', needle(search), delayMatcher(expected), function (args) {
      args = Array.prototype.slice.call(args);
      args[1] = (parseInt(args[1], 10) || 0) * factor;
      return args;
    });
  };

  S['noeval-if'] = function (search) {
    var test = needle(search);
    window.eval = new Proxy(window.eval, {
      apply: function (target, self, args) {
        if (test(String(args[0]))) return undefined;
        return Reflect.apply(target, self, args);
      },
    });
  };

  S['noeval'] = function () { window.eval = function () {}; };

  S['addEventListener-defuser'] = function (type, search) {
    var typeTest = needle(type), handlerTest = needle(search);
    var original = EventTarget.prototype.addEventListener;
    EventTarget.prototype.addEventListener = new Proxy(original, {
      apply: function (target, self, args) {
        var handler = args[1];
        var text = '';
        try { text = typeof handler === 'function' ? Function.prototype.toString.call(handler) : String(handler); } catch (e) {}
        if (typeTest(String(args[0])) && handlerTest(text)) return undefined;
        return Reflect.apply(target, self, args);
      },
    });
  };

  S['window.open-defuser'] = function (search) {
    var test = needle(search);
    var original = typeof window.open === 'function' ? window.open : function () { return null; };
    window.open = new Proxy(original, {
      apply: function (target, self, args) {
        if (!test(String(args[0] || ''))) return Reflect.apply(target, self, args);
        // Fausse fenêtre : le site croit sa pop-up ouverte.
        return { closed: false, close: function () { this.closed = true; }, focus: function () {}, blur: function () {}, location: { href: '' }, document: document.implementation.createHTMLDocument('') };
      },
    });
  };

  S['no-fetch-if'] = function (search) {
    if (!window.fetch) return;
    var test = needle(String(search || '').replace(/^url:/, ''));
    window.fetch = new Proxy(window.fetch, {
      apply: function (target, self, args) {
        var url = args[0] && args[0].url ? args[0].url : String(args[0]);
        if (search !== undefined && search !== '' && test(url)) return Promise.resolve(new Response('', { status: 200, statusText: 'OK' }));
        return Reflect.apply(target, self, args);
      },
    });
  };

  S['no-xhr-if'] = function (search) {
    var test = needle(String(search || '').replace(/^url:/, ''));
    var open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (method, url) {
      this.__widoBlocked = search !== undefined && search !== '' && test(String(url));
      return open.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
      if (!this.__widoBlocked) return send.apply(this, arguments);
      var xhr = this;
      ['readyState', 'status', 'statusText', 'response', 'responseText'].forEach(function (k, i) {
        try { Object.defineProperty(xhr, k, { value: [4, 200, 'OK', '', ''][i] }); } catch (e) {}
      });
      setTimeout(function () {
        ['readystatechange', 'load', 'loadend'].forEach(function (t) { xhr.dispatchEvent(new Event(t)); });
      }, 1);
    };
  };

  S['remove-attr'] = function (attrs, selector) {
    if (!attrs) return;
    var names = attrs.split('|');
    var sel = selector || names.map(function (a) { return '[' + a + ']'; }).join(',');
    function clean() {
      try {
        document.querySelectorAll(sel).forEach(function (el) { names.forEach(function (a) { el.removeAttribute(a); }); });
      } catch (e) {}
    }
    clean();
    onDom(clean);
    document.addEventListener('DOMContentLoaded', clean);
  };

  S['remove-class'] = function (classes, selector) {
    if (!classes) return;
    var names = classes.split('|');
    var sel = selector || names.map(function (c) { return '.' + c; }).join(',');
    function clean() {
      try {
        document.querySelectorAll(sel).forEach(function (el) { names.forEach(function (c) { el.classList.remove(c); }); });
      } catch (e) {}
    }
    clean();
    onDom(clean);
    document.addEventListener('DOMContentLoaded', clean);
  };

  S['remove-node-text'] = function (nodeName, search) {
    if (!nodeName) return;
    var nameTest = needle(nodeName.charAt(0) === '/' ? nodeName : '/^' + nodeName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$/i');
    var test = needle(search);
    onDom(function (node) {
      if (!nameTest(node.nodeName)) return;
      if (node.nodeType === 3) { if (test(node.nodeValue)) node.nodeValue = ''; }
      else if (test(node.textContent)) node.textContent = '';
    });
  };

  S['json-prune'] = function (prune, required) {
    if (!prune) return;
    var paths = prune.split(/ +/), needed = required ? required.split(/ +/) : [];
    function apply(o) {
      if (!o || typeof o !== 'object') return o;
      for (var i = 0; i < needed.length; i++) if (!hasPath(o, needed[i])) return o;
      paths.forEach(function (p) { prunePath(o, p); });
      return o;
    }
    var parse = JSON.parse;
    JSON.parse = new Proxy(parse, { apply: function (t, s, a) { return apply(Reflect.apply(t, s, a)); } });
    if (window.Response) {
      var json = Response.prototype.json;
      Response.prototype.json = new Proxy(json, { apply: function (t, s, a) { return Reflect.apply(t, s, a).then(apply); } });
    }
  };

  S['nowebrtc'] = function () {
    var dummy = function () { this.close = function () {}; this.createDataChannel = function () { return {}; }; this.createOffer = function () { return Promise.resolve(); }; };
    ['RTCPeerConnection', 'webkitRTCPeerConnection'].forEach(function (n) { if (window[n]) window[n] = dummy; });
  };

  // Abréviations et anciens noms utilisés dans les listes.
  var ALIASES = {
    'set': 'set-constant', 'set-constant.js': 'set-constant',
    'aopr': 'abort-on-property-read', 'aopw': 'abort-on-property-write',
    'acs': 'abort-current-script', 'acis': 'abort-current-script', 'abort-current-inline-script': 'abort-current-script',
    'nostif': 'no-setTimeout-if', 'prevent-setTimeout': 'no-setTimeout-if', 'setTimeout-defuser': 'no-setTimeout-if',
    'nosiif': 'no-setInterval-if', 'prevent-setInterval': 'no-setInterval-if', 'setInterval-defuser': 'no-setInterval-if',
    'nano-sib': 'nano-setInterval-booster', 'nano-stb': 'nano-setTimeout-booster',
    'prevent-eval-if': 'noeval-if', 'silent-noeval': 'noeval',
    'aeld': 'addEventListener-defuser', 'prevent-addEventListener': 'addEventListener-defuser',
    'nowoif': 'window.open-defuser', 'no-window-open-if': 'window.open-defuser', 'prevent-window-open': 'window.open-defuser',
    'prevent-fetch': 'no-fetch-if', 'prevent-xhr': 'no-xhr-if',
    'ra': 'remove-attr', 'rc': 'remove-class', 'rmnt': 'remove-node-text',
  };

  /** `nom, arg1, arg2` → [nom, args] ; `\,` garde une virgule dans un argument, les guillemets sont retirés. */
  function parseCall(call) {
    var args = [], current = '';
    for (var i = 0; i < call.length; i++) {
      var c = call.charAt(i);
      if (c === '\\' && call.charAt(i + 1) === ',') { current += ','; i++; continue; }
      if (c === ',') { args.push(current); current = ''; continue; }
      current += c;
    }
    args.push(current);
    args = args.map(function (a) {
      a = a.trim();
      var q = a.charAt(0);
      if ((q === '"' || q === "'" || q === '`') && a.length >= 2 && a.charAt(a.length - 1) === q) a = a.slice(1, -1);
      return a;
    });
    var name = args.shift().replace(/\.js$/, '');
    return [ALIASES[name] || name, args];
  }

  calls.forEach(function (call) {
    var parsed = parseCall(call);
    var fn = S[parsed[0]];
    if (!fn) return;
    try { fn.apply(null, parsed[1]); } catch (e) {}
  });
}
