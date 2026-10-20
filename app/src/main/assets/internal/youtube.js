// YouTube sans publicité. WiDo appelle run(options) au tout début de chaque document, dans toutes
// les frames ; le script ne fait rien hors de YouTube. Trois défenses, de la plus propre à la plus brute :
//   1. les pubs sont retirées des données du lecteur avant qu'il ne les lise (il n'en voit aucune) ;
//   2. si une pub passe quand même, elle est coupée, accélérée jusqu'à sa fin, et « Ignorer » est cliqué ;
//   3. les emplacements publicitaires de l'accueil et de la page vidéo sont masqués.
// Option `sponsor` : saute les passages sponsorisés signalés dans la base collaborative SponsorBlock.
function run(O) {
  var host = location.hostname;
  if (!/(^|\.)youtube(-nocookie)?\.com$/.test(host) || window.__widoYt) return;
  window.__widoYt = true;

  // --- 1. Données du lecteur sans pubs --------------------------------------------------------
  var AD_KEYS = ['adPlacements', 'adSlots', 'playerAds', 'adBreakHeartbeatParams', 'adBreakParams'];
  function strip(o) {
    if (!o || typeof o !== 'object') return;
    for (var i = 0; i < AD_KEYS.length; i++) if (AD_KEYS[i] in o) { try { delete o[AD_KEYS[i]]; } catch (e) {} }
  }
  function prune(o) {
    if (!o || typeof o !== 'object') return o;
    strip(o);
    if (o.playerResponse) strip(o.playerResponse);
    if (Array.isArray(o)) for (var i = 0; i < o.length && i < 8; i++) { strip(o[i]); if (o[i] && o[i].playerResponse) strip(o[i].playerResponse); }
    return o;
  }
  var parse = JSON.parse;
  JSON.parse = function () { return prune(parse.apply(this, arguments)); };
  if (window.Response && Response.prototype.json) {
    var json = Response.prototype.json;
    Response.prototype.json = function () { return json.apply(this, arguments).then(prune); };
  }
  ['ytInitialPlayerResponse', 'playerResponse'].forEach(function (name) {
    var value = window[name];
    try {
      Object.defineProperty(window, name, {
        configurable: true,
        get: function () { return value; },
        set: function (v) { value = prune(v); },
      });
    } catch (e) {}
  });

  // --- 3. Emplacements publicitaires masqués --------------------------------------------------
  var plain = [
    '#masthead-ad', '#player-ads', 'ytd-ad-slot-renderer', 'ytd-in-feed-ad-layout-renderer', 'ytd-banner-promo-renderer',
    'ytd-statement-banner-renderer', 'ytd-promoted-sparkles-web-renderer', 'ytd-promoted-video-renderer',
    'ytd-display-ad-renderer', 'ytd-compact-promoted-video-renderer', 'ytd-action-companion-ad-renderer',
    'ytd-player-legacy-desktop-watch-ads-renderer', 'ytd-mealbar-promo-renderer', 'ytd-enforcement-message-view-model',
    '.ytp-ad-overlay-container', '.ytp-ad-image-overlay', '.ytp-featured-product', 'ad-slot-renderer',
    'ytm-promoted-sparkles-web-renderer', 'ytm-companion-ad-renderer', 'ytm-promoted-video-renderer',
    'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"]',
  ];
  // `:has()` dans une règle à part : un ancien moteur qui ne le comprend pas n'annule pas les autres.
  var withHas = [
    'ytd-rich-item-renderer:has(ytd-ad-slot-renderer)', 'ytd-rich-section-renderer:has(ytd-statement-banner-renderer)',
    'ytm-rich-item-renderer:has(ad-slot-renderer)', 'tp-yt-paper-dialog:has(ytd-enforcement-message-view-model)',
    'tp-yt-paper-dialog:has(ytd-mealbar-promo-renderer)',
  ];
  function addStyle() {
    var s = document.createElement('style');
    s.textContent = plain.join(',') + '{display:none!important}\n' + withHas.join(',') + '{display:none!important}';
    (document.head || document.documentElement).appendChild(s);
  }
  if (document.documentElement) addStyle(); else document.addEventListener('readystatechange', addStyle, { once: true });

  if (window.top !== window && !/youtube(-nocookie)?\.com\/embed\//.test(location.href)) return;

  // --- 2. Pub qui passe quand même : coupée, accélérée, ignorée --------------------------------
  var inAd = false, savedMuted = false, savedRate = 1;
  function player() { return document.querySelector('#movie_player, .html5-video-player'); }
  function guard() {
    var p = player();
    if (!p) return;
    var v = p.querySelector('video');
    var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, button.ytp-ad-skip-button-slot, .ytp-ad-skip-button-container button');
    if (skip) skip.click();
    var ad = p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting');
    if (ad && v) {
      if (!inAd) { inAd = true; savedMuted = v.muted; savedRate = v.playbackRate || 1; }
      v.muted = true;
      if (isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.2) v.currentTime = v.duration - 0.1;
      try { v.playbackRate = 16; } catch (e) {}
    } else if (inAd) {
      inAd = false;
      if (v) { v.muted = savedMuted; try { v.playbackRate = savedRate; } catch (e) {} }
    }
    // Message « bloqueurs de pub interdits » : retiré, la vidéo repart.
    var wall = document.querySelector('ytd-enforcement-message-view-model');
    if (wall) {
      var dialog = wall.closest('tp-yt-paper-dialog');
      if (dialog) dialog.remove();
      if (v && v.paused) v.play().catch(function () {});
    }
  }
  setInterval(guard, 300);

  // --- SponsorBlock ---------------------------------------------------------------------------
  if (!O || !O.sponsor || window.top !== window) return;
  var CATEGORIES = '["sponsor","selfpromo","interaction"]';
  var currentId = null, segments = [], lastSkipEnd = -1;
  function videoId() {
    var m = location.pathname.match(/^\/(?:shorts|live|embed)\/([\w-]{11})/);
    if (m) return m[1];
    var id = new URLSearchParams(location.search).get('v');
    return id && /^[\w-]{11}$/.test(id) ? id : null;
  }
  function refresh() {
    var id = videoId();
    if (id === currentId) return;
    currentId = id;
    segments = [];
    lastSkipEnd = -1;
    if (!id) return;
    fetch('https://sponsor.ajay.app/api/skipSegments?videoID=' + id + '&categories=' + encodeURIComponent(CATEGORIES))
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(function (list) {
        if (id !== currentId || !Array.isArray(list)) return;
        segments = list.map(function (s) { return s.segment; }).filter(function (s) { return s && s[1] - s[0] > 1; });
      })
      .catch(function () {});
  }
  function notify() {
    var bridge = window.WiDoBridge;
    if (bridge && typeof bridge.postMessage === 'function') bridge.postMessage(JSON.stringify({ type: 'skip', what: 'sponsor' }));
  }
  setInterval(function () {
    refresh();
    if (!segments.length || inAd) return;
    var p = player();
    var v = p && p.querySelector('video');
    if (!v) return;
    var t = v.currentTime;
    for (var i = 0; i < segments.length; i++) {
      var s = segments[i];
      if (t >= s[0] && t < s[1] - 0.5 && s[1] !== lastSkipEnd) {
        lastSkipEnd = s[1];
        v.currentTime = s[1];
        notify();
        break;
      }
    }
  }, 400);
}
