/*
 * admin-bulk-products.js: "select all" and bulk hide / make visible on the admin Products page.
 * Uses PUT /api/products/{id}/status?status=hidden|active (admin only).
 */
(function () {
  'use strict';

  function rows() {
    return Array.from(document.querySelectorAll('input.row-select'));
  }

  function selectedIds() {
    return rows().filter(cb => cb.checked).map(cb => cb.getAttribute('data-id'));
  }

  function ensureBar() {
    let bar = document.getElementById('bulkProductsBar');
    if (bar) return bar;
    bar = document.createElement('div');
    bar.id = 'bulkProductsBar';
    bar.className = 'fixed bottom-6 left-1/2 -translate-x-1/2 z-50 hidden items-center gap-3 bg-[#5c4a3d] text-white ' +
      'px-5 py-3 rounded-xl shadow-xl';
    bar.innerHTML =
      '<span data-count class="font-medium"></span>' +
      '<button type="button" data-action="hidden" class="bg-red-600 hover:bg-red-700 px-4 py-2 rounded-lg text-sm font-medium">' +
      '<i class="fas fa-eye-slash mr-1"></i>Hide selected</button>' +
      '<button type="button" data-action="active" class="bg-green-600 hover:bg-green-700 px-4 py-2 rounded-lg text-sm font-medium">' +
      '<i class="fas fa-eye mr-1"></i>Make visible</button>' +
      '<button type="button" data-action="clear" class="px-3 py-2 rounded-lg text-sm hover:bg-white/10">Clear</button>';
    document.body.appendChild(bar);
    bar.addEventListener('click', onAction);
    return bar;
  }

  function refresh() {
    const ids = selectedIds();
    const bar = ensureBar();
    bar.querySelector('[data-count]').textContent = ids.length + ' selected';
    bar.classList.toggle('hidden', ids.length === 0);
    bar.classList.toggle('flex', ids.length > 0);
    const all = document.getElementById('selectAllProducts');
    if (all) {
      const total = rows().length;
      all.checked = total > 0 && ids.length === total;
      all.indeterminate = ids.length > 0 && ids.length < total;
    }
  }

  async function onAction(e) {
    const btn = e.target.closest('button[data-action]');
    if (!btn) return;
    const action = btn.getAttribute('data-action');
    if (action === 'clear') {
      rows().forEach(cb => { cb.checked = false; });
      refresh();
      return;
    }
    const ids = selectedIds();
    const verb = action === 'hidden' ? 'hide' : 'make visible';
    if (!ids.length || !confirm('Are you sure you want to ' + verb + ' ' + ids.length + ' product(s)?')) return;
    btn.disabled = true;
    const results = await Promise.all(ids.map(id =>
      fetch('/api/products/' + encodeURIComponent(id) + '/status?status=' + action, { method: 'PUT' })
        .then(r => r.ok).catch(() => false)));
    const failed = results.filter(ok => !ok).length;
    if (failed) {
      alert(failed + ' product(s) could not be updated.');
    }
    window.location.reload();
  }

  function init() {
    const all = document.getElementById('selectAllProducts');
    if (all) {
      all.addEventListener('change', () => {
        rows().forEach(cb => { cb.checked = all.checked; });
        refresh();
      });
    }
    rows().forEach(cb => cb.addEventListener('change', refresh));
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
