// =============================================================
//  adminReview.js: Review Moderation Page Interactivity
//  ArtsyVibe: Artisan Co-op Platform
// =============================================================
//  Works alongside adminDashboard.js (sidebar, dropdown, logout)
//  and the existing inline script (approveReview, rejectReview,
//  toggleRejectDropdown, toggleReview, confirmApproveAll,
//  executeApproveAll, closeConfirmModal).
//
//  This file adds:
//   1.  Status filter pills  (All / Pending / Approved / Rejected)
//   2.  Search filter        (product name + reviewer name)
//   3.  Category filter      (dropdown)
//   4.  Rating filter        (dropdown)
//   5.  Flag Threshold toggle: highlight / isolate flagged rows
//   6.  Combined filter engine: all filters applied together
//   7.  "Read More / Show Less" toggle on review text
//   8.  "View" eye-button: opens a Read-Only Review Detail modal
//   9.  Approve All Pending: count badge kept in sync
//  10.  Pending count badge : auto-updates after every action
//  11.  Pagination          : simulated page navigation
//  12.  Page-load animations: staggered row fade-in
//  13.  Keyboard: Escape closes any open modal or dropdown
// =============================================================

(() => {
  // ─────────────────────────────────────────────
  // SECTION 0: Utilities
  // ─────────────────────────────────────────────
  const qs  = (sel, ctx = document) => ctx.querySelector(sel);
  const qsa = (sel, ctx = document) => Array.from(ctx.querySelectorAll(sel));

  // ─────────────────────────────────────────────
  // SECTION 1: Status Filter Pills
  // ─────────────────────────────────────────────
  // Clicking All / Pending / Approved / Rejected filters
  // the table rows by their status badge text content.
  // Active pill gets terracotta fill; others revert to ghost.
  function initStatusPills() {
    const pills = qsa('.status-pill');

    pills.forEach(pill => {
      pill.addEventListener('click', () => {
        // Update active styling
        pills.forEach(p => {
          p.classList.remove('bg-[#c17c5f]', 'text-white');
          p.classList.add('text-[#8b7355]', 'hover:bg-[#f5ebe0]');
        });
        pill.classList.add('bg-[#c17c5f]', 'text-white');
        pill.classList.remove('text-[#8b7355]', 'hover:bg-[#f5ebe0]');

        // Server-side filtering: reload with ?status=...
        if (window.ADMIN_SERVER_PAGED) {
          window.AdminPaging.go({ status: pill.dataset.status === 'all' ? null : pill.dataset.status });
          return;
        }
        // Store active status and re-run combined filter
        document.body.dataset.activeStatus = pill.dataset.status;
        applyFilters();
      });
    });

    // Default (or the status the server filtered by)
    const current = (window.AdminPaging && window.AdminPaging.param('status')) || 'all';
    document.body.dataset.activeStatus = current;
    pills.forEach(p => {
      const on = p.dataset.status === current;
      p.classList.toggle('bg-[#c17c5f]', on);
      p.classList.toggle('text-white', on);
      p.classList.toggle('text-[#8b7355]', !on);
      p.classList.toggle('hover:bg-[#f5ebe0]', !on);
    });
    const flagToggle = qs('#flagThresholdToggle');
    if (flagToggle && window.AdminPaging) {
      flagToggle.checked = window.AdminPaging.param('flagged') === 'true';
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 2: Search Filter
  // ─────────────────────────────────────────────
  // Filters rows whose product name OR reviewer name
  // contains the typed string (case-insensitive).
  function initSearchFilter() {
    const searchInput = qs('input[type="text"]');
    if (!searchInput) return;

    searchInput.addEventListener('input', applyFilters);
  }

  // ─────────────────────────────────────────────
  // SECTION 3 & 4: Category & Rating Filters
  // ─────────────────────────────────────────────
  function initDropdownFilters() {
    qs('#categoryFilter')?.addEventListener('change', applyFilters);
    qs('#ratingFilter')?.addEventListener('change', applyFilters);
  }

  function normalizeCategory(value) {
    return (value || '').toLowerCase().trim().replace(/\s+/g, '');
  }

  // ─────────────────────────────────────────────
  // SECTION 6: Combined Filter Engine
  // ─────────────────────────────────────────────
  // Reads all active filter values and shows/hides
  // each <tr> accordingly. Updates the result count label.
  function applyFilters() {
    // Search, filters and paging are done by the server (admin-paging.js).
    if (window.ADMIN_SERVER_PAGED) return;
    const searchVal   = (qs('input[type="text"]')?.value || '').toLowerCase().trim();
    const categoryVal = normalizeCategory(qs('#categoryFilter')?.value);
    const ratingVal   = qs('#ratingFilter')?.value || '';
    const statusVal   = document.body.dataset.activeStatus || 'all';
    const flagOnly    = qs('#flagThresholdToggle')?.checked || false;

    const rows = qsa('tbody tr').filter(row => !row.querySelector('td[colspan]'));
    let visibleCount = 0;

    rows.forEach(row => {
      const productName = qs('td:nth-child(1) p.font-semibold', row)?.textContent?.toLowerCase() || '';
      const reviewerName = qs('td:nth-child(3) span.font-medium', row)?.textContent?.toLowerCase() || '';
      const statusBadge  = qs('.status-badge', row)?.textContent?.trim().toLowerCase() || '';
      const rowRating    = row.dataset.rating || qsa('td:nth-child(4) .fa-star:not(.far)', row).length.toString();
      const isAutoFlagged = qs('.fa-robot', row) !== null;
      const rowCategory  = normalizeCategory(row.dataset.category);

      const matchSearch   = !searchVal || productName.includes(searchVal) || reviewerName.includes(searchVal);
      const matchStatus   = statusVal === 'all'
        || (statusVal === 'active' && statusBadge.includes('active'))
        || (statusVal === 'rejected' && (statusBadge.includes('blocked') || statusBadge.includes('rejected')));
      const matchRating   = !ratingVal || rowRating === ratingVal;
      const matchFlag     = !flagOnly || isAutoFlagged;
      const matchCategory = !categoryVal || rowCategory === categoryVal;

      const visible = matchSearch && matchStatus && matchRating && matchCategory && matchFlag;
      row.style.display = visible ? '' : 'none';
      if (visible) visibleCount++;
    });

    updateFilterResultLabel(visibleCount, rows.length);
  }

  function updateFilterResultLabel(visibleCount, totalCount) {
    const paginationDiv = qs('.flex.items-center.justify-between.px-6.py-4.border-t');
    const labelParagraph = paginationDiv?.querySelector('p.text-sm');
    if (!labelParagraph) return;

    if (visibleCount === 0) {
      labelParagraph.innerHTML = 'No reviews match the selected filters';
    } else {
      labelParagraph.innerHTML = `Showing <span class="font-semibold text-[#5c4a3d]">${visibleCount}</span> of <span class="font-semibold text-[#5c4a3d]">${totalCount}</span> reviews`;
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 5: Flag Threshold Toggle
  // ─────────────────────────────────────────────
  // Overrides the inline toggleFlagThreshold() to also
  // re-run the combined filter and use window.showToast.
  window.toggleFlagThreshold = function () {
    const isChecked = qs('#flagThresholdToggle')?.checked;
    if (window.ADMIN_SERVER_PAGED) {
      window.AdminPaging.go({ flagged: isChecked ? 'true' : null });
      return;
    }
    applyFilters();

    const msg = isChecked
      ? 'Flag Threshold ON: showing auto-flagged reviews only'
      : 'Flag Threshold OFF: showing all reviews';
    const type = isChecked ? 'warning' : 'info';

    if (window.showToast) {
      window.showToast(msg, type);
    }
  };

  // ─────────────────────────────────────────────
  // SECTION 7: "Read More / Show Less" Toggle
  // ─────────────────────────────────────────────
  // Overrides the inline toggleReview() to also update
  // the button label between "Read More" and "Show Less".
  window.toggleReview = function (reviewId) {
    const el  = document.getElementById(reviewId);
    const btn = el?.nextElementSibling;
    if (!el) return;

    const isCollapsed = el.classList.contains('review-text-truncated');
    el.classList.toggle('review-text-truncated');
    if (btn) btn.textContent = isCollapsed ? 'Show Less' : 'Read More';
  };

  // ─────────────────────────────────────────────
  // SECTION 8: "View" Eye Button → Detail Modal
  // ─────────────────────────────────────────────
  // Dynamically builds a read-only Review Detail modal
  // when the eye icon is clicked on Approved or Rejected rows.
  function initViewButtons() {
    // Use event delegation since rows can be re-rendered
    qs('tbody')?.addEventListener('click', e => {
      const eyeBtn = e.target.closest('button[title="View Details"]');
      if (!eyeBtn) return;

      const row = eyeBtn.closest('tr');
      openReviewDetailModal(row);
    });
  }

  function openReviewDetailModal(row) {
    // Gather data from the row
    const productName   = escapeHtml(qs('td:nth-child(1) p.font-semibold', row)?.textContent?.trim() || '-');
    const productSku    = escapeHtml(qs('td:nth-child(1) p.text-xs',        row)?.textContent?.trim() || '-');
    const productImg    = safeUrl(qs('td:nth-child(1) img',              row)?.src  || '');
    const artisanName   = escapeHtml(qs('td:nth-child(2) span.font-medium', row)?.textContent?.trim() || '-');
    const artisanImg    = safeUrl(qs('td:nth-child(2) img',              row)?.src  || '');
    const reviewerName  = escapeHtml(qs('td:nth-child(3) span.font-medium', row)?.textContent?.trim() || '-');
    const reviewerImg   = safeUrl(qs('td:nth-child(3) img',              row)?.src  || '');
    const fullStars     = qsa('td:nth-child(4) .fas.fa-star',    row).length;
    const halfStar      = qs('td:nth-child(4) .fa-star-half-alt', row) ? 0.5 : 0;
    const ratingNum     = fullStars + halfStar;
    const reviewText    = escapeHtml(qs('td:nth-child(5) p',                row)?.textContent?.trim() || '-');
    const submitted     = escapeHtml(qs('td:nth-child(6) span',             row)?.textContent?.trim() || '-');
    const isAutoFlagged = qs('.fa-robot',                        row) !== null;
    const statusBadge   = qs('.status-badge',                    row);
    const statusText    = escapeHtml(statusBadge?.textContent?.trim() || '-');
    const rejectionReason = escapeHtml(qs('p.text-red-600',                 row)?.textContent?.replace('Reason:', '').trim() || null);

    // Build star HTML
    const starsHtml = buildStarHtml(ratingNum);

    // Status colour
    let statusClass = 'bg-amber-100 text-amber-700';
    if (statusText.includes('Approved')) statusClass = 'bg-green-100 text-green-700';
    if (statusText.includes('Rejected')) statusClass = 'bg-red-100 text-red-700';

    // Create / reuse modal
    let modal = qs('#reviewDetailModal');
    if (!modal) {
      modal = document.createElement('div');
      modal.id = 'reviewDetailModal';
      modal.className = 'fixed inset-0 modal-overlay z-50 hidden items-center justify-center p-4';
      document.body.appendChild(modal);
    }

    modal.innerHTML = `
      <div class="modal-content bg-white rounded-2xl w-full max-w-lg shadow-2xl overflow-hidden">
        <!-- Header -->
        <div class="bg-[#f5ebe0] px-6 py-4 flex items-center justify-between">
          <h3 class="text-xl font-bold text-[#5c4a3d]" style="font-family:'Playfair Display',serif">Review Details</h3>
          <button id="closeDetailModal" class="text-[#8b7355] hover:text-[#c17c5f] transition-colors">
            <i class="fas fa-times text-xl"></i>
          </button>
        </div>

        <div class="p-6 space-y-5 overflow-y-auto max-h-[75vh]">
          <!-- Product -->
          <div class="flex items-center gap-4 p-4 bg-[#faf9f6] rounded-xl">
            ${productImg ? `<img src="${productImg}" class="w-16 h-16 rounded-lg object-cover" alt="${productName}">` : ''}
            <div>
              <p class="font-bold text-[#5c4a3d]">${productName}</p>
              <p class="text-xs text-[#8b7355]">${productSku}</p>
            </div>
          </div>

          <!-- Artisan + Reviewer -->
          <div class="grid grid-cols-2 gap-4">
            <div class="p-3 bg-[#faf9f6] rounded-xl">
              <p class="text-xs text-[#8b7355] mb-2 font-medium uppercase tracking-wide">Artisan</p>
              <div class="flex items-center gap-2">
                ${artisanImg ? `<img src="${artisanImg}" class="w-8 h-8 rounded-full object-cover">` : ''}
                <span class="text-sm font-semibold text-[#5c4a3d]">${artisanName}</span>
              </div>
            </div>
            <div class="p-3 bg-[#faf9f6] rounded-xl">
              <p class="text-xs text-[#8b7355] mb-2 font-medium uppercase tracking-wide">Reviewer</p>
              <div class="flex items-center gap-2">
                ${reviewerImg ? `<img src="${reviewerImg}" class="w-8 h-8 rounded-full object-cover">` : ''}
                <span class="text-sm font-semibold text-[#5c4a3d]">${reviewerName}</span>
              </div>
            </div>
          </div>

          <!-- Rating + Date -->
          <div class="flex items-center justify-between">
            <div class="star-rating text-xl">${starsHtml}</div>
            <span class="text-sm text-[#8b7355]"><i class="fas fa-calendar-alt mr-1"></i>${submitted}</span>
          </div>

          <!-- Review Text -->
          <div class="bg-[#faf9f6] rounded-xl p-4">
            <p class="text-xs text-[#8b7355] font-medium uppercase tracking-wide mb-2">Review</p>
            <p class="text-[#5c4a3d] text-sm leading-relaxed">${reviewText}</p>
          </div>

          <!-- Auto-flag indicator -->
          ${isAutoFlagged ? `
            <div class="flex items-center gap-2 bg-amber-50 border border-amber-200 rounded-xl px-4 py-3">
              <i class="fas fa-robot text-amber-500"></i>
              <span class="text-sm text-amber-700 font-medium">Auto-flagged for inappropriate content</span>
            </div>` : ''}

          <!-- Status -->
          <div class="flex items-center justify-between">
            <span class="text-sm text-[#8b7355] font-medium">Moderation Status</span>
            <span class="status-badge ${statusClass} px-3 py-1 rounded-full text-xs font-semibold">
              ${statusText}
            </span>
          </div>

          <!-- Rejection reason if present -->
          ${rejectionReason ? `
            <div class="bg-red-50 border border-red-200 rounded-xl px-4 py-3">
              <p class="text-xs text-red-600 font-medium uppercase tracking-wide mb-1">Rejection Reason</p>
              <p class="text-sm text-red-700 font-semibold">${rejectionReason}</p>
            </div>` : ''}
        </div>

        <!-- Footer -->
        <div class="px-6 py-4 border-t border-[#e5e0d8] flex justify-end">
          <button id="closeDetailModalFooter" class="bg-[#c17c5f] hover:bg-[#a5664d] text-white px-6 py-2.5 rounded-lg font-medium transition-colors">
            Close
          </button>
        </div>
      </div>
    `;

    // Show modal
    modal.classList.remove('hidden');
    modal.classList.add('flex');
    document.body.style.overflow = 'hidden';

    // Close handlers
    const close = () => {
      modal.classList.add('hidden');
      modal.classList.remove('flex');
      document.body.style.overflow = 'auto';
    };

    qs('#closeDetailModal',       modal).addEventListener('click', close);
    qs('#closeDetailModalFooter', modal).addEventListener('click', close);
    modal.addEventListener('click', e => { if (e.target === modal) close(); });
  }

  function buildStarHtml(rating) {
    let html = '';
    for (let i = 1; i <= 5; i++) {
      if (i <= Math.floor(rating))       html += '<i class="fas fa-star"></i>';
      else if (i - 0.5 <= rating)        html += '<i class="fas fa-star-half-alt"></i>';
      else                               html += '<i class="far fa-star"></i>';
    }
    return html;
  }

  // ─────────────────────────────────────────────
  // SECTION 9 & 10: Pending Count Badge Sync
  // ─────────────────────────────────────────────
  // Patches the existing approveReview, rejectReview,
  // and executeApproveAll to also call updatePendingCount()
  // after they mutate the DOM.

  function updatePendingCount() {
    const pendingCount = qsa('.status-badge.bg-amber-100').length;
    // Update the modal's pending count span
    const countEl = qs('#pendingCount');
    if (countEl) countEl.textContent = pendingCount;

    // Update "Approve All Pending" button text
    const approveAllBtn = qs('button[onclick="confirmApproveAll()"]');
    if (approveAllBtn) {
      approveAllBtn.innerHTML = `
        <i class="fas fa-check-double"></i>
        Approve All Pending ${pendingCount > 0 ? `<span class="ml-1 bg-white text-green-700 text-xs font-bold px-2 py-0.5 rounded-full">${pendingCount}</span>` : ''}
      `;
      // Disable if nothing to approve
      approveAllBtn.disabled = pendingCount === 0;
      approveAllBtn.classList.toggle('opacity-50', pendingCount === 0);
      approveAllBtn.classList.toggle('cursor-not-allowed', pendingCount === 0);
    }
  }

  // Patch approveReview
  const _origApprove = window.approveReview;
  window.approveReview = function (button) {
    if (_origApprove) _origApprove(button);
    updatePendingCount();
    applyFilters();
    if (window.showToast) window.showToast('Review approved successfully', 'success');
  };

  // Patch rejectReview
  const _origReject = window.rejectReview;
  window.rejectReview = function (button, reason) {
    if (_origReject) _origReject(button, reason);
    updatePendingCount();
    applyFilters();
    if (window.showToast) window.showToast(`Review rejected: ${escapeHtml(reason)}`, 'warning');
  };

  // Patch executeApproveAll
  const _origExecApprove = window.executeApproveAll;
  window.executeApproveAll = function () {
    if (_origExecApprove) _origExecApprove();
    updatePendingCount();
    applyFilters();
    if (window.showToast) window.showToast('All pending reviews approved', 'success');
  };

  // ─────────────────────────────────────────────
  // SECTION 11: Pagination
  // ─────────────────────────────────────────────
  // Simulated pagination: dynamically calculates pages based on review count,
  // generates page buttons, highlights active page, and updates the label.
  function initPagination() {
    let currentPage = 1;
    const perPage = 5;
    
    // Get total reviews from DOM - count all tbody rows except the "no reviews found" row
    const rows = qsa('tbody tr:not(:has(td[colspan]))');
    const totalReviews = rows.length;
    const totalPages = Math.max(1, Math.ceil(totalReviews / perPage));

    const paginationControls = qs('#paginationControls');
    if (!paginationControls) return;

    const prevBtn = paginationControls.querySelector('.pagination-prev');
    const nextBtn = paginationControls.querySelector('.pagination-next');

    // Generate page buttons dynamically
    function generatePageButtons() {
      const container = paginationControls;
      // Remove old page buttons
      qsa('.pagination-btn', container).forEach(btn => btn.remove());
      const dots = container.querySelector('.pagination-dots');
      if (dots) dots.remove();

      const fragment = document.createDocumentFragment();

      // Determine which page buttons to show
      const maxVisiblePages = 3;
      let startPage = 1;
      let endPage = Math.min(totalPages, maxVisiblePages);

      if (totalPages > maxVisiblePages) {
        if (currentPage > maxVisiblePages - 1) {
          startPage = Math.min(currentPage, totalPages - maxVisiblePages + 1);
          endPage = Math.min(startPage + maxVisiblePages - 1, totalPages);
        }
      }

      // Add page buttons
      for (let p = startPage; p <= endPage; p++) {
        const btn = document.createElement('button');
        btn.className = 'px-4 py-2 rounded-lg font-medium transition-colors pagination-btn';
        btn.dataset.page = p;
        btn.textContent = p;

        if (p === currentPage) {
          btn.className += ' bg-[#c17c5f] text-white';
        } else {
          btn.className += ' border border-[#e5e0d8] text-[#8b7355] hover:bg-[#f5ebe0]';
        }

        btn.addEventListener('click', () => {
          currentPage = p;
          updatePagination();
        });

        fragment.appendChild(btn);
      }

      // Add dots and last page button if needed
      if (endPage < totalPages) {
        const dots = document.createElement('span');
        dots.className = 'text-[#8b7355] pagination-dots';
        dots.textContent = '...';
        fragment.appendChild(dots);

        const lastBtn = document.createElement('button');
        lastBtn.className = 'px-4 py-2 border border-[#e5e0d8] rounded-lg text-[#8b7355] hover:bg-[#f5ebe0] transition-colors pagination-btn';
        lastBtn.dataset.page = totalPages;
        lastBtn.textContent = totalPages;
        lastBtn.addEventListener('click', () => {
          currentPage = totalPages;
          updatePagination();
        });
        fragment.appendChild(lastBtn);
      }

      // Insert before next button
      paginationControls.insertBefore(fragment, nextBtn);
    }

    function updatePagination() {
      // Regenerate page buttons with updated active state
      generatePageButtons();

      // Disable Prev/Next at boundaries
      if (prevBtn) prevBtn.disabled = currentPage === 1;
      if (nextBtn) nextBtn.disabled = currentPage === totalPages;

      // Update label
      const from = (currentPage - 1) * perPage + 1;
      const to = Math.min(currentPage * perPage, totalReviews);
      updatePaginationLabel(from, to, totalReviews);

      // Scroll to top of table
      qs('main')?.scrollTo({ top: 0, behavior: 'smooth' });
    }

    // Attach event listeners to Prev/Next buttons
    prevBtn?.addEventListener('click', () => {
      if (currentPage > 1) { currentPage--; updatePagination(); }
    });

    nextBtn?.addEventListener('click', () => {
      if (currentPage < totalPages) { currentPage++; updatePagination(); }
    });

    // Initialize pagination
    generatePageButtons();
    updatePagination();
  }

  function updatePaginationLabel(from, to, total) {
    const paginationDiv = qs('.flex.items-center.justify-between.px-6.py-4.border-t');
    if (!paginationDiv) return;

    const labelParagraph = paginationDiv.querySelector('p.text-sm');
    if (labelParagraph) {
      if (total === 0) {
        labelParagraph.innerHTML = 'No reviews to display';
      } else {
        labelParagraph.innerHTML = `Showing <span class="font-semibold text-[#5c4a3d]">${from}</span> to <span class="font-semibold text-[#5c4a3d]">${to}</span> of <span class="font-semibold text-[#5c4a3d]">${total}</span> reviews`;
      }
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 12: Page Load Row Animations
  // ─────────────────────────────────────────────
  function initRowAnimations() {
    qsa('tbody tr').forEach((row, i) => {
      row.style.opacity   = '0';
      row.style.transform = 'translateY(12px)';
      row.style.transition = `opacity 0.35s ease ${i * 80}ms, transform 0.35s ease ${i * 80}ms`;

      requestAnimationFrame(() => requestAnimationFrame(() => {
        row.style.opacity   = '1';
        row.style.transform = 'translateY(0)';
      }));
    });
  }

  // ─────────────────────────────────────────────
  // SECTION 13: Keyboard: Escape closes modals
  // ─────────────────────────────────────────────
  function initKeyboardShortcuts() {
    document.addEventListener('keydown', e => {
      if (e.key !== 'Escape') return;

      // Close rejection dropdowns
      qsa('.rejection-dropdown').forEach(d => d.classList.add('hidden'));

      // Close review detail modal
      const detailModal = qs('#reviewDetailModal');
      if (detailModal && !detailModal.classList.contains('hidden')) {
        detailModal.classList.add('hidden');
        detailModal.classList.remove('flex');
        document.body.style.overflow = 'auto';
      }

      // Confirm modal handled by inline script already
    });
  }

  // ─────────────────────────────────────────────
  // INIT
  // ─────────────────────────────────────────────
  document.addEventListener('DOMContentLoaded', () => {
    initStatusPills();
    initSearchFilter();
    initDropdownFilters();
    initViewButtons();
    initPagination();
    initRowAnimations();
    initKeyboardShortcuts();
    updatePendingCount();   // set initial badge state
  });

})();
