/*
 * app-core.js: load FIRST on every page (in <head>, before any other script).
 *
 * 1. CSRF: Spring Security sets an XSRF-TOKEN cookie; every same-origin state-changing fetch()
 *    must echo it in the X-XSRF-TOKEN header. This wraps window.fetch so existing code needs no changes.
 * 2. Session sync: the server session is the source of truth. sessionStorage only mirrors it for the UI.
 *    - A 401 from /api/** means the server session is gone: clear the mirror and go to the login page.
 *    - sessionStorage is per tab, so a new tab of a signed-in user starts "logged out"; restore it once.
 * 3. escapeHtml(): use it for every piece of data interpolated into innerHTML.
 */
(function () {
  'use strict';

  var AUTH_KEYS = ['loggedIn', 'isLoggedIn', 'userEmail', 'userName', 'userRole', 'userId', 'userProfile',
    'cartCount', 'notificationCount'];
  var SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS', 'TRACE'];

  function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  /** For values placed in src/href attributes: allows http(s), site-relative and data:image URLs only. */
  function safeUrl(value, fallback) {
    var v = value == null ? '' : String(value).trim();
    var lower = v.toLowerCase();
    if (lower.indexOf('https://') === 0 || lower.indexOf('http://') === 0 ||
        (v.charAt(0) === '/' && v.charAt(1) !== '/') ||
        (lower.indexOf('data:image/') === 0 && lower.indexOf('data:image/svg') !== 0)) {
      return escapeHtml(v);
    }
    return escapeHtml(fallback || '');
  }

  function readCookie(name) {
    var match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
    return match ? decodeURIComponent(match[1]) : null;
  }

  function isLoggedInMirror() {
    try {
      return sessionStorage.getItem('loggedIn') === 'true';
    } catch (e) {
      return false;
    }
  }

  function clearAuthMirror() {
    try {
      AUTH_KEYS.forEach(function (k) { sessionStorage.removeItem(k); });
    } catch (e) { /* storage unavailable */ }
  }

  function storeAuthMirror(user) {
    sessionStorage.setItem('loggedIn', 'true');
    sessionStorage.setItem('isLoggedIn', 'true');
    sessionStorage.setItem('userEmail', user.email || '');
    sessionStorage.setItem('userName', user.name || '');
    sessionStorage.setItem('userRole', user.role || '');
    sessionStorage.setItem('userId', String(user.userId));
    sessionStorage.setItem('userProfile', JSON.stringify(user));
  }

  function isAuthEndpoint(path) {
    return path.indexOf('/api/auth/') === 0;
  }

  /** Only same-site paths are allowed as post-login redirect targets (prevents open redirects). */
  function safeNextPath(next) {
    if (!next || typeof next !== 'string') return null;
    if (next.charAt(0) !== '/' || next.charAt(1) === '/' || next.charAt(1) === '\\') return null;
    return next;
  }

  var originalFetch = window.fetch.bind(window);
  var redirectingToLogin = false;

  window.fetch = function (input, init) {
    init = init || {};
    var url;
    try {
      url = new URL(typeof input === 'string' ? input : input.url, window.location.href);
    } catch (e) {
      return originalFetch(input, init);
    }
    var sameOrigin = url.origin === window.location.origin;
    var method = (init.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();

    if (sameOrigin && SAFE_METHODS.indexOf(method) === -1) {
      var token = readCookie('XSRF-TOKEN');
      if (token) {
        var headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
        headers.set('X-XSRF-TOKEN', token);
        init = Object.assign({}, init, { headers: headers });
      }
    }

    return originalFetch(input, init).then(function (response) {
      if (response.status === 401 && sameOrigin && url.pathname.indexOf('/api/') === 0
          && !isAuthEndpoint(url.pathname) && isLoggedInMirror() && !redirectingToLogin) {
        redirectingToLogin = true;
        clearAuthMirror();
        var next = window.location.pathname + window.location.search;
        window.location.href = '/login?message=' + encodeURIComponent('Your session has expired. Please log in again.')
          + '&next=' + encodeURIComponent(next);
      }
      return response;
    });
  };

  // New tab of a signed-in user: restore the UI mirror from the server session, then reload once
  // so page scripts (auth guard, nav) see the signed-in state.
  if (!isLoggedInMirror()) {
    var syncFlag = 'authSessionSynced';
    var alreadyTried = false;
    try { alreadyTried = sessionStorage.getItem(syncFlag) === '1'; } catch (e) { alreadyTried = true; }
    if (!alreadyTried) {
      try { sessionStorage.setItem(syncFlag, '1'); } catch (e) { /* ignore */ }
      originalFetch('/api/auth/session', { credentials: 'same-origin' })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (data) {
          if (data && data.success && data.user) {
            storeAuthMirror(data.user);
            window.location.reload();
          }
        })
        .catch(function () { /* offline or server down: stay logged out in the UI */ });
    }
  }

  // Any image that fails to load (dead link, missing file) shows a neutral placeholder instead of a broken icon.
  document.addEventListener('error', function (e) {
    var img = e.target;
    if (img && img.tagName === 'IMG' && !img.dataset.fallbackApplied) {
      img.dataset.fallbackApplied = '1';
      img.src = '/images/placeholder.svg';
    }
  }, true);

  window.AppCore = {
    escapeHtml: escapeHtml,
    safeUrl: safeUrl,
    clearAuthMirror: clearAuthMirror,
    storeAuthMirror: storeAuthMirror,
    safeNextPath: safeNextPath,
    csrfToken: function () { return readCookie('XSRF-TOKEN'); }
  };
  // Many page scripts already call a global escapeHtml(); provide one where they don't define their own.
  if (typeof window.escapeHtml !== 'function') {
    window.escapeHtml = escapeHtml;
  }
  window.safeUrl = safeUrl;
})();
