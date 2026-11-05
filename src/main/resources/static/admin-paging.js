/*
 * admin-paging.js — server-side search, filters and paging for admin list pages.
 *
 * - Elements with data-filter-param="name" become filters: changing a select, or pressing Enter /
 *   leaving a text box, reloads the page with ?name=value (and back to page 1).
 * - #serverPager (templates/fragments/pager.html) gets Previous / page numbers / Next links that keep
 *   the current filters.
 * - window.ADMIN_SERVER_PAGED tells the page's own script to skip its old in-browser filtering/paging.
 */
(function () {
  'use strict';

  window.ADMIN_SERVER_PAGED = true;

  function urlWith(changes) {
    var params = new URLSearchParams(window.location.search);
    Object.keys(changes).forEach(function (key) {
      var value = changes[key];
      if (value === null || value === undefined || value === '') {
        params['delete'](key);
      } else {
        params.set(key, value);
      }
    });
    var query = params.toString();
    return window.location.pathname + (query ? '?' + query : '');
  }

  function bindFilters() {
    document.querySelectorAll('[data-filter-param]').forEach(function (el) {
      var param = el.getAttribute('data-filter-param');
      var current = new URLSearchParams(window.location.search).get(param);
      if (current !== null && el.value !== current) {
        el.value = current;
      }
      var apply = function () {
        var value = (el.value || '').trim();
        if (value === (current || '')) return;
        var change = { page: null };
        change[param] = value;
        window.location.href = urlWith(change);
      };
      if (el.tagName === 'SELECT') {
        el.addEventListener('change', apply);
      } else {
        el.addEventListener('keydown', function (e) {
          if (e.key === 'Enter') {
            e.preventDefault();
            apply();
          }
        });
        el.addEventListener('change', apply);
      }
    });
  }

  function link(label, page, opts) {
    opts = opts || {};
    var a = document.createElement(opts.disabled ? 'span' : 'a');
    a.textContent = label;
    var base = 'px-4 py-2 rounded-lg text-sm transition-colors ';
    if (opts.active) {
      a.className = base + 'bg-[#c17c5f] text-white font-medium';
      a.setAttribute('aria-current', 'page');
    } else if (opts.disabled) {
      a.className = base + 'border border-[#e5e0d8] text-[#8b7355] opacity-50 cursor-not-allowed';
    } else {
      a.className = base + 'border border-[#e5e0d8] text-[#8b7355] hover:border-[#c17c5f] hover:text-[#c17c5f]';
    }
    if (!opts.disabled) {
      a.href = urlWith({ page: page > 1 ? String(page) : null });
    }
    return a;
  }

  function renderPager() {
    var pager = document.getElementById('serverPager');
    if (!pager) return;
    var controls = pager.querySelector('[data-server-pager-controls]');
    var page = parseInt(pager.getAttribute('data-page'), 10) || 1;
    var totalPages = parseInt(pager.getAttribute('data-total-pages'), 10) || 1;
    if (!controls || totalPages <= 1) return;

    controls.appendChild(link('Previous', page - 1, { disabled: page <= 1 }));
    var shown = [];
    for (var p = 1; p <= totalPages; p++) {
      if (p === 1 || p === totalPages || Math.abs(p - page) <= 2) shown.push(p);
    }
    var last = 0;
    shown.forEach(function (p) {
      if (p - last > 1) {
        var dots = document.createElement('span');
        dots.className = 'px-2 text-[#8b7355]';
        dots.textContent = '…';
        controls.appendChild(dots);
      }
      controls.appendChild(link(String(p), p, { active: p === page }));
      last = p;
    });
    controls.appendChild(link('Next', page + 1, { disabled: page >= totalPages }));
  }

  /** For page scripts: reload with changed query parameters (back to page 1). */
  window.AdminPaging = {
    go: function (changes) {
      window.location.href = urlWith(Object.assign({ page: null }, changes));
    },
    param: function (name) {
      return new URLSearchParams(window.location.search).get(name);
    }
  };

  function init() {
    bindFilters();
    renderPager();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
