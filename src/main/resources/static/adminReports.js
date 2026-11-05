// adminReports.js — Reports & Analytics (database-backed via /api/admin/reports)

(() => {
  const qs = (sel, ctx = document) => ctx.querySelector(sel);
  const qsa = (sel, ctx = document) => Array.from(ctx.querySelectorAll(sel));

  const DONUT_COLORS = ['#c17c5f', '#8b7355', '#a5664d', '#d4c5b5', '#f5ebe0', '#e5e0d8'];
  const REVENUE_X = [80, 180, 280, 380, 480, 580];
  const BAR_SLOTS = [
    { cx: 70, custX: 60, artX: 85 },
    { cx: 170, custX: 160, artX: 185 },
    { cx: 270, custX: 260, artX: 285 },
    { cx: 370, custX: 360, artX: 385 },
    { cx: 470, custX: 460, artX: 485 },
    { cx: 570, custX: 560, artX: 585 },
  ];

  let currentReport = null;
  let loading = false;

  function formatBd(amount) {
    const n = Number(amount) || 0;
    return 'BD ' + n.toLocaleString('en-US', { maximumFractionDigits: 0 });
  }

  function formatChange(change) {
    const n = Number(change) || 0;
    const up = n >= 0;
    return {
      up,
      text: `${Math.abs(n).toFixed(1)}%`,
      className: `text-sm font-semibold flex items-center gap-1 ${up ? 'text-green-600' : 'text-red-500'}`,
      icon: up ? 'fa-arrow-up' : 'fa-arrow-down',
    };
  }

  function showToast(message, type = 'info') {
    if (window.showToast) {
      window.showToast(message, type);
      return;
    }
    const el = document.createElement('div');
    const colors = { success: '#16a34a', error: '#dc2626', warning: '#d97706', info: '#c17c5f' };
    el.className = 'fixed bottom-4 right-4 text-white px-6 py-3 rounded-xl shadow-lg z-50';
    el.style.backgroundColor = colors[type] || colors.info;
    el.textContent = message;
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 3000);
  }

  function buildReportUrl() {
    const range = qs('#dateRangeSelect')?.value || 'month';
    const params = new URLSearchParams({ range });
    if (range === 'custom') {
      const start = qs('#customStartDate')?.value;
      const end = qs('#customEndDate')?.value;
      if (start) params.set('start', start);
      if (end) params.set('end', end);
    }
    return `/api/admin/reports?${params.toString()}`;
  }

  async function fetchReport() {
    if (loading) return currentReport;
    loading = true;
    try {
      const res = await fetch(buildReportUrl(), { credentials: 'same-origin' });
      if (!res.ok) throw new Error('Failed to load report data');
      currentReport = await res.json();
      applyReport(currentReport);
      return currentReport;
    } catch (err) {
      console.error(err);
      showToast('Could not load report data. Please try again.', 'error');
      return null;
    } finally {
      loading = false;
    }
  }

  function updateKpiCards(data) {
    const cards = {
      revenue: {
        value: formatBd(data.totalRevenue),
        change: data.totalRevenueChange,
        subtitle: 'orders in selected period',
      },
      orders: {
        value: String(data.totalOrders ?? 0),
        change: data.totalOrdersChange,
        subtitle: 'orders in selected period',
      },
      newUsers: {
        value: String(data.newUsers ?? 0),
        change: data.newUsersChange,
        subtitle: 'customers registered in period',
      },
      artisans: {
        value: String(data.activeArtisans ?? 0),
        change: data.activeArtisansChange,
        subtitle: `${data.newArtisansInPeriod ?? 0} new artisans in period`,
      },
      auctionRev: {
        value: formatBd(data.auctionRevenue),
        change: data.auctionRevenueChange,
        subtitle: 'from ended auctions in period',
      },
    };

    qsa('#kpiCards [data-kpi]').forEach(card => {
      const key = card.dataset.kpi;
      const kpi = cards[key];
      if (!kpi) return;

      const valueEl = qs('.kpi-value', card);
      const changeEl = qs('.kpi-change', card);
      const subtitleEl = qs('.kpi-subtitle', card);
      const ch = formatChange(kpi.change);

      if (valueEl) {
        valueEl.textContent = key === 'orders' || key === 'newUsers' || key === 'artisans'
          ? kpi.value
          : kpi.value;
        if (key === 'revenue' || key === 'auctionRev') {
          valueEl.innerHTML = kpi.value;
        }
      }
      if (subtitleEl) subtitleEl.textContent = kpi.subtitle;
      if (changeEl) {
        changeEl.className = ch.className + ' kpi-change';
        changeEl.innerHTML = `<i class="fas ${ch.icon}"></i><span>${ch.text}</span>`;
      }
    });
  }

  function updateRevenueChart(data) {
    const svg = qs('#revenueLineSvg');
    if (!svg) return;

    const values = normalizeArray(data.revenuePerWeek, 6);
    const labels = data.weekLabels || ['P1', 'P2', 'P3', 'P4', 'P5', 'P6'];
    const maxVal = Math.max(...values, 1);

    const points = values.map((val, i) => ({
      x: REVENUE_X[i],
      y: Math.max(20, 200 - (val / maxVal) * 150),
      val,
    }));

    const linePath = qs('#revenueLinePath', svg);
    const areaPath = qs('#revenueAreaPath', svg);
    const d = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');
    if (linePath) linePath.setAttribute('d', d);
    if (areaPath) areaPath.setAttribute('d', `${d} L 580 200 L 80 200 Z`);

    const pointsG = qs('#revenueDataPoints', svg);
    if (pointsG) {
      pointsG.innerHTML = points.map((p, i) =>
        `<circle cx="${p.x}" cy="${p.y}" r="5" fill="#c17c5f" data-value="${p.val}" data-label="${labels[i] || ''}"></circle>`
      ).join('');
    }

    const xLabels = qs('#revenueXLabels', svg);
    if (xLabels) {
      xLabels.innerHTML = labels.map((lbl, i) =>
        `<text x="${REVENUE_X[i]}" y="225" class="text-xs fill-[#8b7355]" text-anchor="middle">${lbl}</text>`
      ).join('');
    }

    const yLabels = svg.querySelectorAll('text[text-anchor="end"]');
    if (yLabels.length >= 4) {
      yLabels[0].textContent = '0';
      yLabels[1].textContent = formatAxis(maxVal * 0.33);
      yLabels[2].textContent = formatAxis(maxVal * 0.67);
      yLabels[3].textContent = formatAxis(maxVal);
    }
  }

  function formatAxis(n) {
    if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
    return Math.round(n).toString();
  }

  function updateCategoryChart(data) {
    const names = data.categoryNames || [];
    const pcts = data.categoryPercentages || [];
    const counts = data.categoryCounts || [];
    const total = data.totalOrders ?? 0;

    const totalEl = qs('#categoryDonutTotal');
    if (totalEl) totalEl.textContent = total;

    const circumference = 2 * Math.PI * 40;
    const segG = qs('#categoryDonutSegments');
    if (segG) {
      let offset = 0;
      let html = `<circle cx="50" cy="50" r="40" fill="none" stroke="#e5e0d8" stroke-width="20"/>`;
      if (names.length === 0) {
        html += `<circle cx="50" cy="50" r="40" fill="none" stroke="#e5e0d8" stroke-width="20" stroke-dasharray="${circumference} 0"/>`;
      } else {
        names.forEach((name, i) => {
          const pct = Number(pcts[i]) || 0;
          const len = (pct / 100) * circumference;
          const color = DONUT_COLORS[i % DONUT_COLORS.length];
          html += `<circle cx="50" cy="50" r="40" fill="none" stroke="${color}" stroke-width="20" stroke-dasharray="${len} ${circumference - len}" stroke-dashoffset="${-offset}" transform="rotate(-90 50 50)"/>`;
          offset += len;
        });
      }
      segG.innerHTML = html;
    }

    const legend = qs('#categoryLegend');
    if (legend) {
      if (names.length === 0) {
        legend.innerHTML = '<div class="text-sm text-[#8b7355]">No category data available</div>';
      } else {
        legend.innerHTML = names.map((name, i) => `
          <div class="flex items-center justify-between category-legend-row">
            <div class="flex items-center gap-2">
              <div class="w-4 h-4 rounded" style="background-color:${DONUT_COLORS[i % DONUT_COLORS.length]}"></div>
              <span class="text-sm text-[#5c4a3d]">${escapeHtml(name)}</span>
            </div>
            <span class="text-sm font-semibold text-[#5c4a3d]">${pcts[i]}%</span>
          </div>
        `).join('');
      }
    }
  }

  function updateRegistrationsChart(data) {
    const svg = qs('#registrationsBarSvg');
    if (!svg) return;

    const customers = normalizeArray(data.regCustomers, 6);
    const artisans = normalizeArray(data.regArtisans, 6);
    const labels = data.weekLabels || ['P1', 'P2', 'P3', 'P4', 'P5', 'P6'];
    const maxVal = Math.max(...customers, ...artisans, 1);
    const yBase = 200;
    const maxH = 150;

    const barsG = qs('#registrationsBars', svg);
    if (barsG) {
      let html = '';
      BAR_SLOTS.forEach((slot, i) => {
        const cVal = customers[i] || 0;
        const aVal = artisans[i] || 0;
        const cH = Math.round((cVal / maxVal) * maxH);
        const aH = Math.round((aVal / maxVal) * maxH);
        html += `<rect x="${slot.custX}" y="${yBase - cH}" width="20" height="${cH}" fill="#8b5cf6" rx="2" data-type="customer" data-count="${cVal}" data-label="${labels[i]}"/>`;
        html += `<rect x="${slot.artX}" y="${yBase - aH}" width="20" height="${aH}" fill="#f59e0b" rx="2" data-type="artisan" data-count="${aVal}" data-label="${labels[i]}"/>`;
      });
      barsG.innerHTML = html;
    }

    const xLabels = qs('#registrationsXLabels', svg);
    if (xLabels) {
      xLabels.innerHTML = labels.map((lbl, i) =>
        `<text x="${BAR_SLOTS[i].cx}" y="225" class="text-xs fill-[#8b7355]" text-anchor="middle">${lbl}</text>`
      ).join('');
    }

    const yLabels = svg.querySelectorAll('text[text-anchor="end"]');
    if (yLabels.length >= 4) {
      const maxLabel = Math.ceil(maxVal);
      yLabels[0].textContent = '0';
      yLabels[1].textContent = String(Math.round(maxLabel * 0.33));
      yLabels[2].textContent = String(Math.round(maxLabel * 0.67));
      yLabels[3].textContent = String(maxLabel);
    }
  }

  function updateTopArtisans(data) {
    const list = qs('#topArtisansList');
    if (!list) return;

    const names = data.topArtisanNames || [];
    const revenues = data.topArtisanRevenues || [];
    const pcts = data.topArtisanPercentages || [];

    if (names.length === 0) {
      list.innerHTML = '<div class="text-sm text-[#8b7355] text-center py-8">No artisan revenue data available</div>';
      return;
    }

    list.innerHTML = names.map((name, i) => {
      const initial = (name || '?').charAt(0).toUpperCase();
      const rev = formatBd(revenues[i]);
      const pct = pcts[i] ?? 0;
      return `
        <div>
          <div class="flex items-center justify-between mb-2">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-full bg-[#c17c5f] flex items-center justify-center text-white font-bold">${escapeHtml(initial)}</div>
              <span class="text-sm font-medium text-[#5c4a3d]">${escapeHtml(name)}</span>
            </div>
            <span class="text-sm font-bold text-[#c17c5f]">${rev}</span>
          </div>
          <div class="w-full bg-[#e5e0d8] rounded-full h-3">
            <div class="bg-[#c17c5f] h-3 rounded-full transition-all duration-700" style="width:${pct}%"></div>
          </div>
        </div>
      `;
    }).join('');
  }

  function renderStars(avg) {
    const rating = Number(avg) || 0;
    let html = '';
    for (let i = 1; i <= 5; i++) {
      if (rating >= i) html += '<i class="fas fa-star"></i>';
      else if (rating >= i - 0.5) html += '<i class="fas fa-star-half-alt"></i>';
      else html += '<i class="far fa-star"></i>';
    }
    return html + `<span class="text-[#8b7355] ml-1">(${rating.toFixed(1)})</span>`;
  }

  function rankBadge(rank) {
    if (rank === 1) return `<div class="w-8 h-8 bg-gradient-to-br from-yellow-400 to-yellow-600 rounded-full flex items-center justify-center text-white font-bold text-sm">${rank}</div>`;
    if (rank === 2) return `<div class="w-8 h-8 bg-gradient-to-br from-gray-300 to-gray-500 rounded-full flex items-center justify-center text-white font-bold text-sm">${rank}</div>`;
    if (rank === 3) return `<div class="w-8 h-8 bg-gradient-to-br from-amber-600 to-amber-800 rounded-full flex items-center justify-center text-white font-bold text-sm">${rank}</div>`;
    return `<div class="w-8 h-8 bg-[#e5e0d8] rounded-full flex items-center justify-center text-[#5c4a3d] font-bold text-sm">${rank}</div>`;
  }

  function updateBestProductsTable(data) {
    const tbody = qs('#bestProductsBody');
    if (!tbody) return;

    const products = data.bestProducts || [];
    if (products.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" class="px-6 py-8 text-center text-[#8b7355]">No product sales data available</td></tr>';
      return;
    }

    tbody.innerHTML = products.map(bp => {
      const rank = bp.rank;
      const rowClass = rank === 1 ? 'rank-gold' : rank === 2 ? 'rank-silver' : rank === 3 ? 'rank-bronze' : '';
      const img = bp.imageUrl || '/images/Logo.png';
      return `
        <tr class="table-row-hover ${rowClass}">
          <td class="px-6 py-4">${rankBadge(rank)}</td>
          <td class="px-6 py-4">
            <div class="flex items-center gap-3">
              <img src="${escapeAttr(img)}" alt="Product" class="w-14 h-14 rounded-lg object-cover" onerror="this.src='/images/Logo.png'"/>
              <div>
                <p class="font-semibold text-[#5c4a3d]">${escapeHtml(bp.title || '')}</p>
                <p class="text-xs text-[#8b7355]">ID: ${bp.productId ?? ''}</p>
              </div>
            </div>
          </td>
          <td class="px-6 py-4"><span class="font-medium text-[#5c4a3d]">${escapeHtml(bp.artisanName || '')}</span></td>
          <td class="px-6 py-4"><span class="text-[#8b7355] text-sm">${escapeHtml(bp.category || '')}</span></td>
          <td class="px-6 py-4"><span class="font-semibold text-[#5c4a3d]">${bp.unitsSold ?? 0}</span></td>
          <td class="px-6 py-4"><span class="font-bold text-green-600">${formatBd(bp.revenue)}</span></td>
          <td class="px-6 py-4"><div class="star-rating text-sm">${renderStars(bp.avgRating)}</div></td>
        </tr>
      `;
    }).join('');
  }

  function applyReport(data) {
    if (!data) return;
    updateKpiCards(data);
    updateRevenueChart(data);
    updateCategoryChart(data);
    updateRegistrationsChart(data);
    updateTopArtisans(data);
    updateBestProductsTable(data);
  }

  function normalizeArray(arr, len) {
    const a = Array.isArray(arr) ? arr.slice() : [];
    while (a.length < len) a.push(0);
    return a.slice(0, len);
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function escapeAttr(s) {
    return escapeHtml(s).replace(/'/g, '&#39;');
  }

  async function updateDateRange() {
    const select = qs('#dateRangeSelect');
    const customRange = qs('#customDateRange');
    if (!select) return;

    if (select.value === 'custom') {
      customRange?.classList.remove('hidden');
      const start = qs('#customStartDate')?.value;
      const end = qs('#customEndDate')?.value;
      if (!start || !end) {
        showToast('Select start and end dates for custom range', 'warning');
        return;
      }
    } else {
      customRange?.classList.add('hidden');
    }

    const report = await fetchReport();
    if (report) {
      showToast(`Showing data for: ${report.label || select.options[select.selectedIndex].text}`, 'info');
    }
  }

  window.updateDateRange = updateDateRange;

  window.downloadReport = function () {
    const tbody = qs('#bestProductsBody');
    if (!tbody) return;

    const label = qs('#dateRangeSelect')?.options[qs('#dateRangeSelect').selectedIndex]?.text || 'report';
    const headers = ['Rank', 'Product', 'Artisan', 'Category', 'Units Sold', 'Revenue', 'Avg Rating'];
    const rows = [headers.join(',')];

    qsa('tr', tbody).forEach(row => {
      const cells = row.querySelectorAll('td');
      if (cells.length < 7) return;
      const cols = Array.from(cells).map(c => `"${c.textContent.replace(/\s+/g, ' ').trim().replace(/"/g, '""')}"`);
      rows.push(cols.join(','));
    });

    const blob = new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `artisyvibe-report-${label.toLowerCase().replace(/\s+/g, '-')}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    showToast('Report downloaded as CSV', 'success');
  };

  function initCustomDateValidation() {
    const startInput = qs('#customStartDate');
    const endInput = qs('#customEndDate');
    if (!startInput || !endInput) return;

    const applyCustom = () => {
      if (qs('#dateRangeSelect')?.value === 'custom' && startInput.value && endInput.value) {
        if (startInput.value > endInput.value) {
          showToast('End date must be after start date', 'error');
          return;
        }
        updateDateRange();
      }
    };
    startInput.addEventListener('change', applyCustom);
    endInput.addEventListener('change', applyCustom);
  }

  document.addEventListener('DOMContentLoaded', () => {
    initCustomDateValidation();

    if (window.__INITIAL_REPORT__) {
      try {
        currentReport = typeof window.__INITIAL_REPORT__ === 'string'
          ? JSON.parse(window.__INITIAL_REPORT__)
          : window.__INITIAL_REPORT__;
        applyReport(currentReport);
      } catch (e) {
        fetchReport();
      }
    } else {
      fetchReport();
    }

    if (qs('#dateRangeSelect')?.value === 'custom') {
      qs('#customDateRange')?.classList.remove('hidden');
    }
  });
})();
