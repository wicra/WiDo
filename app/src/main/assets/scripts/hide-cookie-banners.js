// Masque les bandeaux de consentement courants (sélecteurs génériques).
(function () {
  if (window.__widoCookies) return;
  window.__widoCookies = true;
  var css = '#onetrust-banner-sdk,#CybotCookiebotDialog,.cookie-banner,.cookie-consent,.cc-window,[id*="cookie-banner"],[class*="cookie-banner"],[aria-label*="cookie" i]{display:none!important}';
  var s = document.createElement('style');
  s.textContent = css;
  (document.head || document.documentElement).appendChild(s);
})();
