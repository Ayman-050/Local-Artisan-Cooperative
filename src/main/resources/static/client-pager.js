/*
 * client-pager.js: paging for small lists that are filtered in the browser (an artisan's own orders/products).
 *
 *   const pager = ClientPager({ rows: () => [...tr elements], footer: element, noun: 'orders', pageSize: 10 });
 *   pager.refresh(true);   // call after the page's filter has set each row's display (resets to page 1)
 *
 * The page's filter decides which rows match (by showing/hiding them); the pager then shows one page of the
 * matching rows. Previous/Next and page numbers are disabled when there is nowhere to go.
 */
(function () {
  'use strict';

  window.ClientPager = function (opts) {
    const pageSize = opts.pageSize || 10;
    const footer = opts.footer;
    let page = 1;

    footer.innerHTML = '<p class="text-sm text-[#8b7355]" data-pager-label></p>' +
      '<nav class="flex items-center gap-2" data-pager-controls aria-label="Pagination"></nav>';
    const label = footer.querySelector('[data-pager-label]');
    const controls = footer.querySelector('[data-pager-controls]');

    function button(text, target, state) {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = text;
      const base = 'px-4 py-2 rounded-lg text-sm transition-colors ';
      if (state === 'active') {
        b.className = base + 'bg-[#c17c5f] text-white font-medium';
        b.setAttribute('aria-current', 'page');
      } else if (state === 'disabled') {
        b.className = base + 'border border-[#e5e0d8] text-[#8b7355] opacity-50 cursor-not-allowed';
        b.disabled = true;
      } else {
        b.className = base + 'border border-[#e5e0d8] text-[#8b7355] hover:border-[#c17c5f] hover:text-[#c17c5f]';
        b.addEventListener('click', () => { page = target; apply(); });
      }
      return b;
    }

    function apply() {
      const matching = opts.rows().filter(r => r.dataset.pagerMatch === '1');
      const totalPages = Math.max(1, Math.ceil(matching.length / pageSize));
      page = Math.min(Math.max(page, 1), totalPages);
      const start = (page - 1) * pageSize;

      matching.forEach((row, i) => {
        const onPage = i >= start && i < start + pageSize;
        if (!onPage) {
          row.style.setProperty('display', 'none', 'important');
        } else if (row.dataset.pagerDisplay) {
          row.style.setProperty('display', row.dataset.pagerDisplay, 'important');
        } else {
          row.style.removeProperty('display');
        }
      });

      label.textContent = matching.length === 0
        ? 'No ' + opts.noun + ' found'
        : 'Showing ' + (start + 1) + '–' + Math.min(start + pageSize, matching.length) + ' of ' + matching.length + ' ' + opts.noun;

      controls.innerHTML = '';
      controls.appendChild(button('Previous', page - 1, page <= 1 ? 'disabled' : ''));
      for (let p = 1; p <= totalPages; p++) {
        controls.appendChild(button(String(p), p, p === page ? 'active' : ''));
      }
      controls.appendChild(button('Next', page + 1, page >= totalPages ? 'disabled' : ''));
    }

    return {
      /** Re-read which rows the page's filter left visible, then show the first (or current) page. */
      refresh: function (resetPage) {
        opts.rows().forEach(row => {
          const visible = row.style.display !== 'none';
          row.dataset.pagerMatch = visible ? '1' : '0';
          row.dataset.pagerDisplay = visible ? row.style.display : '';
        });
        if (resetPage) page = 1;
        apply();
      }
    };
  };
})();
