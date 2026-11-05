// adminAuction.js — Admin Auctions Management (DB-backed)

(() => {
  const PER_PAGE = 10;
  let currentStatus = 'live';
  let currentPage = 1;
  let matchedRows = [];
  let countdownInterval = null;
  let monitorAuctionId = null;

  function formatBd(amount) {
    const n = Number(amount) || 0;
    return 'BD ' + (Number.isInteger(n) ? n.toLocaleString() : n.toFixed(1));
  }

  function applyFilters() {
    const category = (document.getElementById('categoryFilter')?.value || '').toLowerCase();
    const artisan = (document.getElementById('artisanFilter')?.value || '').toLowerCase();
    const search = (document.getElementById('searchInput')?.value || '').toLowerCase().trim();

    const counts = { live: 0, upcoming: 0, ended: 0, cancelled: 0 };
    matchedRows = [];

    document.querySelectorAll('.auction-row').forEach(row => {
      const status = row.getAttribute('data-status') || '';
      const rowCategory = (row.getAttribute('data-category') || '').toLowerCase();
      const rowArtisan = (row.getAttribute('data-artisan') || '').toLowerCase();
      const rowText = row.innerText.toLowerCase();

      if (counts[status] !== undefined) counts[status]++;

      let match = status === currentStatus;
      if (category && rowCategory !== category) match = false;
      if (artisan && rowArtisan !== artisan) match = false;
      if (search && !rowText.includes(search)) match = false;

      if (match) {
        matchedRows.push(row);
      }
    });

    const countLive = document.getElementById('count-live');
    const countUpcoming = document.getElementById('count-upcoming');
    const countEnded = document.getElementById('count-ended');
    const countCancelled = document.getElementById('count-cancelled');
    if (countLive) countLive.textContent = counts.live || 0;
    if (countUpcoming) countUpcoming.textContent = counts.upcoming || 0;
    if (countEnded) countEnded.textContent = counts.ended || 0;
    if (countCancelled) countCancelled.textContent = counts.cancelled || 0;

    currentPage = 1;
    renderPagination();
  }

  function renderPagination() {
    const total = matchedRows.length;
    const totalPages = Math.max(1, Math.ceil(total / PER_PAGE));
    if (currentPage > totalPages) currentPage = totalPages;
    if (currentPage < 1) currentPage = 1;

    matchedRows.forEach((row, idx) => {
      const start = (currentPage - 1) * PER_PAGE;
      const end = currentPage * PER_PAGE;
      row.style.display = idx >= start && idx < end ? '' : 'none';
    });

    document.querySelectorAll('.auction-row').forEach(row => {
      if (!matchedRows.includes(row)) row.style.display = 'none';
    });

    const label = document.getElementById('paginationLabel');
    if (label) {
      if (total === 0) {
        label.innerHTML = 'No auctions match the selected filters';
      } else {
        const from = (currentPage - 1) * PER_PAGE + 1;
        const to = Math.min(currentPage * PER_PAGE, total);
        label.innerHTML =
          `Showing <span class="font-semibold text-[#5c4a3d]">${from}</span> to ` +
          `<span class="font-semibold text-[#5c4a3d]">${to}</span> of ` +
          `<span class="font-semibold text-[#5c4a3d]">${total}</span> auctions`;
      }
    }

    const pagesEl = document.getElementById('paginationPages');
    const prevBtn = document.getElementById('paginationPrev');
    const nextBtn = document.getElementById('paginationNext');
    if (!pagesEl) return;

    pagesEl.innerHTML = '';
    for (let p = 1; p <= totalPages; p++) {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.textContent = String(p);
      btn.className = p === currentPage
        ? 'px-4 py-2 bg-[#c17c5f] text-white rounded-lg font-medium'
        : 'px-4 py-2 border border-[#e5e0d8] rounded-lg text-[#8b7355] hover:bg-[#f5ebe0] transition-colors';
      btn.addEventListener('click', () => {
        currentPage = p;
        renderPagination();
      });
      pagesEl.appendChild(btn);
    }

    if (prevBtn) {
      prevBtn.disabled = currentPage <= 1;
      prevBtn.onclick = () => {
        if (currentPage > 1) {
          currentPage--;
          renderPagination();
        }
      };
    }
    if (nextBtn) {
      nextBtn.disabled = currentPage >= totalPages;
      nextBtn.onclick = () => {
        if (currentPage < totalPages) {
          currentPage++;
          renderPagination();
        }
      };
    }
  }

  window.switchTab = function (tab) {
    currentStatus = tab;
    document.querySelectorAll('.tab-button').forEach(btn => {
      btn.classList.remove('active', 'bg-[#c17c5f]', 'text-white');
      btn.classList.add('text-[#8b7355]');
    });
    const target = event?.target?.closest?.('.tab-button');
    if (target) {
      target.classList.add('active', 'bg-[#c17c5f]', 'text-white');
      target.classList.remove('text-[#8b7355]');
    }
    applyFilters();
  };

  function populateMonitorModal(data) {
    const img = document.getElementById('monitorProductImage');
    if (img) {
      img.src = data.productImageUrl || '/images/Logo.png';
    }
    const title = document.getElementById('monitorProductTitle');
    if (title) title.textContent = data.productTitle || '—';
    const sku = document.getElementById('monitorProductSku');
    if (sku) sku.textContent = 'SKU: ' + (data.productSku || '—');
    const desc = document.getElementById('monitorProductDesc');
    if (desc) desc.textContent = data.productDescription || '—';

    const artisanImg = document.getElementById('monitorArtisanImage');
    if (artisanImg) artisanImg.src = data.artisanProfilePicture || '/images/Logo.png';
    const artisanName = document.getElementById('monitorArtisanName');
    if (artisanName) artisanName.textContent = data.artisanName || '—';
    const artisanCat = document.getElementById('monitorArtisanCategory');
    if (artisanCat) artisanCat.textContent = data.productCategory ? data.productCategory + ' artisan' : 'Artisan';

    const meta = document.getElementById('monitorArtisanMeta');
    if (meta) {
      const rating = data.artisanAvgRating || 0;
      const reviews = data.artisanReviewCount || 0;
      const auctions = data.artisanAuctionCount || 0;
      meta.innerHTML =
        `<span><i class="fas fa-star text-amber-500 mr-1"></i>${rating} (${reviews} reviews)</span>` +
        `<span><i class="fas fa-gavel mr-1"></i>${auctions} auctions</span>`;
    }

    const currentBid = document.getElementById('currentBid');
    if (currentBid) {
      currentBid.textContent = data.currentHighestBid > 0 ? formatBd(data.currentHighestBid) : '—';
    }
    const bidDelta = document.getElementById('monitorBidDelta');
    if (bidDelta) {
      const delta = Number(data.lastBidDelta) || 0;
      if (delta > 0) {
        bidDelta.innerHTML = `<i class="fas fa-arrow-up mr-1"></i> ${formatBd(delta)} from last bid`;
      } else {
        bidDelta.textContent = 'No bid increase yet';
      }
    }

    const endLabel = document.getElementById('monitorEndLabel');
    if (endLabel) endLabel.textContent = data.endTimeLabel || '—';

    const starting = document.getElementById('monitorStartingBid');
    if (starting) starting.textContent = formatBd(data.startingBid);
    const totalBids = document.getElementById('monitorTotalBids');
    if (totalBids) totalBids.textContent = String(data.totalBids ?? 0);
    const unique = document.getElementById('monitorUniqueBidders');
    if (unique) unique.textContent = String(data.uniqueBidders ?? 0);

    const tbody = document.getElementById('monitorBidHistoryBody');
    if (tbody) {
      const history = data.bidHistory || [];
      if (history.length === 0) {
        tbody.innerHTML =
          '<tr><td colspan="3" class="px-4 py-6 text-center text-sm text-[#8b7355]">No bids yet</td></tr>';
      } else {
        tbody.innerHTML = history.map((bid, i) => {
          const imgSrc = bid.bidderImage || '/images/Logo.png';
          const amountClass = i === 0 ? 'font-bold text-green-600' : 'font-bold text-[#5c4a3d]';
          return `<tr>
            <td class="px-4 py-3">
              <div class="flex items-center gap-2">
                <img src="${escapeHtml(imgSrc)}" class="w-8 h-8 rounded-full object-cover" alt="Bidder" onerror="this.src='/images/Logo.png'"/>
                <span class="text-sm font-medium text-[#5c4a3d]">${escapeHtml(bid.bidderName)}</span>
              </div>
            </td>
            <td class="px-4 py-3"><span class="${amountClass}">${formatBd(bid.bidAmount)}</span></td>
            <td class="px-4 py-3"><span class="text-sm text-[#8b7355]">${escapeHtml(bid.timeAgo)}</span></td>
          </tr>`;
        }).join('');
      }
    }

    const badge = document.getElementById('monitorStatusBadge');
    if (badge) {
      const st = (data.displayStatus || 'LIVE').toUpperCase();
      if (st === 'LIVE') {
        badge.className = 'status-badge bg-green-100 text-green-700';
        badge.innerHTML = '<span class="pulse-dot"></span> Live';
      } else {
        badge.className = 'status-badge bg-gray-100 text-gray-700';
        badge.textContent = st;
      }
    }
  }

  function escapeHtml(s) {
    return String(s || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function startCountdown(secondsRemaining) {
    if (countdownInterval) clearInterval(countdownInterval);
    let remaining = Math.max(0, Number(secondsRemaining) || 0);
    const el = document.getElementById('countdown');
    if (!el) return;

    const tick = () => {
      if (!document.getElementById('monitorModal')?.classList.contains('flex')) {
        clearInterval(countdownInterval);
        return;
      }
      const h = Math.floor(remaining / 3600);
      const m = Math.floor((remaining % 3600) / 60);
      const s = remaining % 60;
      el.textContent =
        `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
      if (remaining <= 0) {
        clearInterval(countdownInterval);
        return;
      }
      remaining--;
    };

    tick();
    countdownInterval = setInterval(tick, 1000);
  }

  window.openMonitorModal = async function (auctionId) {
    monitorAuctionId = auctionId;
    const modal = document.getElementById('monitorModal');
    if (!modal) return;

    modal.classList.remove('hidden');
    modal.classList.add('flex');
    document.body.style.overflow = 'hidden';

    const tbody = document.getElementById('monitorBidHistoryBody');
    if (tbody) {
      tbody.innerHTML =
        '<tr><td colspan="3" class="px-4 py-6 text-center text-sm text-[#8b7355]">Loading bid history…</td></tr>';
    }

    try {
      const res = await fetch(`/api/admin/auctions/${auctionId}/monitor`, { credentials: 'same-origin' });
      if (!res.ok) throw new Error('Failed to load auction');
      const json = await res.json();
      if (!json.success || !json.data) throw new Error(json.message || 'No data');
      populateMonitorModal(json.data);
      startCountdown(json.data.secondsRemaining);
    } catch (err) {
      console.error(err);
      showNotification('Could not load auction monitor data.', 'error');
    }
  };

  window.closeMonitorModal = function () {
    if (countdownInterval) clearInterval(countdownInterval);
    const modal = document.getElementById('monitorModal');
    if (modal) {
      modal.classList.add('hidden');
      modal.classList.remove('flex');
    }
    document.body.style.overflow = 'auto';
  };

  window.openEndedModal = async function (auctionId) {
    const modal = document.getElementById('endedModal');
    if (!modal) return;
    modal.classList.remove('hidden');
    modal.classList.add('flex');
    document.body.style.overflow = 'hidden';

    try {
      const res = await fetch(`/api/admin/auctions/${auctionId}/monitor`, { credentials: 'same-origin' });
      if (res.ok) {
        const json = await res.json();
        if (json.success && json.data) {
          populateEndedModal(json.data);
        }
      }
    } catch (e) {
      console.error(e);
    }
  };

  function populateEndedModal(data) {
    const title = document.querySelector('#endedModal h3.text-xl');
    if (title) title.textContent = data.productTitle || '—';
    const winning = document.querySelector('#endedModal .text-4xl.font-bold.text-green-600');
    if (winning) winning.textContent = data.currentHighestBid > 0 ? formatBd(data.currentHighestBid) : '—';
  }

  window.closeEndedModal = function () {
    const modal = document.getElementById('endedModal');
    if (modal) {
      modal.classList.add('hidden');
      modal.classList.remove('flex');
    }
    document.body.style.overflow = 'auto';
  };

  window.handleMonitorAction = async function (action) {
    if (!monitorAuctionId) {
      showNotification('No auction is currently being monitored.', 'error');
      return;
    }

    // --- EXTEND ---
    if (action === 'extend') {
      const btn = document.querySelector('button[onclick="handleMonitorAction(\'extend\')"]');
      const orig = btn ? btn.innerHTML : '';
      if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Extending…'; }
      try {
        const res = await fetch(`/api/admin/auctions/${monitorAuctionId}/extend?minutes=30`, {
          method: 'POST', credentials: 'same-origin'
        });
        const json = await res.json();
        if (json.success) {
          showNotification(json.message || 'Auction extended by 30 minutes.', 'success');
          // Re-fetch monitor data so countdown reflects new end time
          const res2 = await fetch(`/api/admin/auctions/${monitorAuctionId}/monitor`, { credentials: 'same-origin' });
          const json2 = await res2.json();
          if (json2.success && json2.data) {
            populateMonitorModal(json2.data);
            startCountdown(json2.data.secondsRemaining);
          }
        } else {
          showNotification(json.message || 'Failed to extend auction.', 'error');
        }
      } catch (err) {
        console.error(err);
        showNotification('Could not extend auction. Please try again.', 'error');
      } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = orig; }
      }

    // --- CANCEL ---
    } else if (action === 'cancel') {
      if (!confirm('Are you sure you want to cancel this live auction? This cannot be undone.')) return;
      const btn = document.querySelector('button[onclick="handleMonitorAction(\'cancel\')"]');
      const orig = btn ? btn.innerHTML : '';
      if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Cancelling…'; }
      try {
        const res = await fetch(`/api/admin/auctions/${monitorAuctionId}/cancel`, {
          method: 'POST', credentials: 'same-origin'
        });
        const json = await res.json();
        if (json.success) {
          showNotification(json.message || 'Auction cancelled.', 'info');
          closeMonitorModal();
          // Update the auction row badge in the table to "cancelled"
          const row = document.querySelector(`.auction-row[data-id="${monitorAuctionId}"]`);
          if (row) {
            row.setAttribute('data-status', 'cancelled');
            const badge = row.querySelector('.status-badge');
            if (badge) {
              badge.className = 'status-badge bg-red-100 text-red-700';
              badge.innerHTML = '<i class="fas fa-times-circle"></i>Cancelled';
            }
            applyFilters();
          }
        } else {
          showNotification(json.message || 'Failed to cancel auction.', 'error');
        }
      } catch (err) {
        console.error(err);
        showNotification('Could not cancel auction. Please try again.', 'error');
      } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = orig; }
      }

    // --- FLAG ---
    } else if (action === 'flag') {
      const reason = prompt('Reason for flagging this auction (optional):') ?? '';
      const btn = document.querySelector('button[onclick="handleMonitorAction(\'flag\')"]');
      const orig = btn ? btn.innerHTML : '';
      if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Flagging…'; }
      try {
        const params = reason.trim() ? `?reason=${encodeURIComponent(reason.trim())}` : '';
        const res = await fetch(`/api/admin/auctions/${monitorAuctionId}/flag${params}`, {
          method: 'POST', credentials: 'same-origin'
        });
        const json = await res.json();
        if (json.success) {
          showNotification(json.message || 'Auction flagged for review.', 'warning');
          // Visual indicator on the flag button
          if (btn) {
            btn.innerHTML = '<i class="fas fa-flag mr-2"></i>Flagged';
            btn.classList.replace('bg-amber-500', 'bg-red-500');
            btn.classList.replace('hover:bg-amber-600', 'hover:bg-red-600');
          }
        } else {
          showNotification(json.message || 'Failed to flag auction.', 'error');
          if (btn) { btn.disabled = false; btn.innerHTML = orig; }
        }
      } catch (err) {
        console.error(err);
        showNotification('Could not flag auction. Please try again.', 'error');
        if (btn) { btn.disabled = false; btn.innerHTML = orig; }
      }
    }
  };

  window.showNotification = function (message, type = 'info') {
    let bgColor = '#c17c5f';
    if (type === 'success') bgColor = '#16a34a';
    else if (type === 'error') bgColor = '#dc2626';
    else if (type === 'warning') bgColor = '#d97706';

    const notification = document.createElement('div');
    notification.className =
      'fixed bottom-4 right-4 text-white px-6 py-3 rounded-xl shadow-lg z-50 transition-opacity duration-300';
    notification.style.backgroundColor = bgColor;
    notification.textContent = message;
    document.body.appendChild(notification);
    setTimeout(() => {
      notification.style.opacity = '0';
      setTimeout(() => notification.remove(), 300);
    }, 3000);
  };

  document.addEventListener('DOMContentLoaded', () => {
    const catFilter = document.getElementById('categoryFilter');
    const artFilter = document.getElementById('artisanFilter');
    const searchInput = document.getElementById('searchInput');
    if (catFilter) catFilter.addEventListener('change', applyFilters);
    if (artFilter) artFilter.addEventListener('change', applyFilters);
    if (searchInput) searchInput.addEventListener('input', applyFilters);
    applyFilters();

    document.getElementById('monitorModal')?.addEventListener('click', e => {
      if (e.target.id === 'monitorModal') closeMonitorModal();
    });
    document.getElementById('endedModal')?.addEventListener('click', e => {
      if (e.target.id === 'endedModal') closeEndedModal();
    });
    document.addEventListener('keydown', e => {
      if (e.key === 'Escape') {
        closeMonitorModal();
        closeEndedModal();
      }
    });
  });
})();
