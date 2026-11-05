// =============================================================
//  adminDashboard.js — Admin Dashboard Interactivity
//  ArtsyVibe — Artisan Co-op Platform
// =============================================================
//  Features:
//   1. Sidebar active link detection based on current URL
//   2. User profile dropdown (hover + click + outside close)
//   3. Logout with confirmation, session cleanup, and redirect
//   4. KPI stat cards — animated count-up on page load
//   5. Quick Action buttons — navigate to correct admin pages
//   6. Recent Activity "View" links — navigate to correct pages
//   7. Orders per Week chart — live tooltip on data point hover
//   8. Revenue bar chart — hover highlight + tooltip
//   9. Toast notification system for user feedback
//  10. Real-time clock in navbar (optional utility)
//  11. API fetch utilities for live KPI stats (ready to enable)
//  12. Keyboard accessibility (Escape closes dropdown)
// =============================================================

(() => {
  // ─────────────────────────────────────────────
  // SECTION 0 — Utility Helpers
  // ─────────────────────────────────────────────
  const qs  = (sel, ctx = document) => ctx.querySelector(sel);
  const qsa = (sel, ctx = document) => Array.from(ctx.querySelectorAll(sel));

  // ─────────────────────────────────────────────
  // SECTION 1 — Sidebar Active Link
  // ─────────────────────────────────────────────
  // Compares the current URL path to each sidebar link's href
  // and applies the .active class + correct colour to the match.
  function setActiveSidebarLink() {
    const path = window.location.pathname.toLowerCase();

    qsa('.sidebar-link').forEach(link => {
      // Reset all links to inactive state
      link.classList.remove('active', 'text-white');
      link.classList.add('text-[#d4c5b5]');

      const href = (link.getAttribute('href') || '').toLowerCase().replace(/^\//, '');

      // Match: the current path must include the link's href segment
      if (href && path.includes(href)) {
        link.classList.add('active', 'text-white');
        link.classList.remove('text-[#d4c5b5]');
      }
    });

    // Always highlight Dashboard when on the root admin page
    if (path === '/admindashboard' || path === '/admin' || path === '/') {
      const dashLink = qs('a[href="/adminDashboard"]');
      if (dashLink) {
        dashLink.classList.add('active', 'text-white');
        dashLink.classList.remove('text-[#d4c5b5]');
      }
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 2 — User Profile Dropdown
  // ─────────────────────────────────────────────
  // Supports both hover (desktop) and click (mobile/touch).
  // Closes automatically when the user clicks outside
  // or presses the Escape key.
  function initUserMenu() {
    const btn      = qs('#userMenuButton');
    const dropdown = qs('#userMenuDropdown');
    const chevron  = qs('#chevronIcon');
    const menu     = qs('.user-menu');

    if (!btn || !dropdown || !chevron || !menu) return;

    let hideTimeout = null;

    const showMenu = () => {
      clearTimeout(hideTimeout);
      dropdown.classList.add('show');
      chevron.classList.add('rotated');
      btn.setAttribute('aria-expanded', 'true');
    };

    const hideMenu = (immediate = false) => {
      if (immediate) {
        dropdown.classList.remove('show');
        chevron.classList.remove('rotated');
        btn.setAttribute('aria-expanded', 'false');
      } else {
        hideTimeout = setTimeout(() => {
          dropdown.classList.remove('show');
          chevron.classList.remove('rotated');
          btn.setAttribute('aria-expanded', 'false');
        }, 150);
      }
    };

    // Hover behaviour (desktop)
    menu.addEventListener('mouseenter', showMenu);
    menu.addEventListener('mouseleave', () => hideMenu());

    // Click/tap toggle (mobile)
    btn.addEventListener('click', e => {
      e.stopPropagation();
      dropdown.classList.contains('show') ? hideMenu(true) : showMenu();
    });

    // Close on outside click
    document.addEventListener('click', e => {
      if (!menu.contains(e.target)) hideMenu(true);
    });

    // Close on Escape key
    document.addEventListener('keydown', e => {
      if (e.key === 'Escape') hideMenu(true);
    });

    // Accessibility
    btn.setAttribute('aria-haspopup', 'true');
    btn.setAttribute('aria-expanded', 'false');
  }

  // ─────────────────────────────────────────────
  // SECTION 3 — Logout
  // ─────────────────────────────────────────────
  // Shows a confirmation dialog, clears the session,
  // then uses replace() so the back button can't return
  // to a protected page.
  function initLogout() {
    const logoutBtn = qs('#logoutButton');
    if (!logoutBtn) return;

    logoutBtn.addEventListener('click', () => {
      const confirmed = window.confirm('Are you sure you want to logout?');
      if (confirmed) {
        const userStr = localStorage.getItem('adminUser') || localStorage.getItem('user') || sessionStorage.getItem('userProfile');
        const user = userStr ? JSON.parse(userStr) : null;
        const email = user ? (user.email || null) : sessionStorage.getItem('userEmail');

        const doLogout = () => {
          sessionStorage.clear();
          localStorage.removeItem('adminToken');
          localStorage.removeItem('adminUser');
          localStorage.removeItem('user');
          showToast('Logged out successfully. Redirecting…', 'info');
          setTimeout(() => window.location.replace('/login'), 1200);
        };

        if (email) {
          fetch('/api/auth/logout', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email })
          }).finally(doLogout);
        } else {
          doLogout();
        }
      }
    });
  }

  // ─────────────────────────────────────────────
  // SECTION 4 — KPI Stat Card Count-Up Animation
  // ─────────────────────────────────────────────
  // Animates the numeric values in the KPI cards from 0
  // up to their final value when the page first loads.
  // Uses IntersectionObserver so it only fires when the
  // cards enter the viewport.
  function initStatCardAnimations() {
    const statNumbers = qsa('.stat-card p.text-2xl');

    const animateCount = (el) => {
      // Parse the raw text — strip commas and convert to number
      const raw    = el.textContent.replace(/,/g, '').trim();
      const target = parseFloat(raw);
      if (isNaN(target)) return;

      const duration = 1200; // ms
      const steps    = 60;
      const increment = target / steps;
      let current = 0;
      let step = 0;

      const isInteger = Number.isInteger(target);

      const timer = setInterval(() => {
        step++;
        current += increment;
        if (step >= steps) {
          clearInterval(timer);
          // Restore exact original value with proper formatting
          el.textContent = isInteger
            ? Math.round(target).toLocaleString()
            : target.toLocaleString();
        } else {
          el.textContent = isInteger
            ? Math.round(current).toLocaleString()
            : current.toFixed(0).toLocaleString();
        }
      }, duration / steps);
    };

    // Use IntersectionObserver for performance
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          animateCount(entry.target);
          observer.unobserve(entry.target); // animate only once
        }
      });
    }, { threshold: 0.3 });

    statNumbers.forEach(el => observer.observe(el));
  }

  // ─────────────────────────────────────────────
  // SECTION 5 — Quick Action Button Navigation
  // ─────────────────────────────────────────────
  // Wires the three Quick Action "Review Now" / "Moderate Now"
  // buttons to their correct admin pages.
  function initQuickActionButtons() {
    // Map: button text content → target URL
    const actionMap = {
      'Review Now'    : null,   // handled per-card below
      'Moderate Now'  : '/adminReview',
    };

    const quickCards = qsa('.quick-action-card');

    quickCards.forEach(card => {
      const btn   = qs('button', card);
      const title = qs('h3', card)?.textContent?.trim() || '';

      if (!btn) return;

      let targetUrl = '';

      if (title.includes('Artisan Approvals')) {
        targetUrl = '/adminArtisanApproval';
      } else if (title.includes('Refund')) {
        targetUrl = '/adminRefund';
      } else if (title.includes('Review') || title.includes('Flagged')) {
        targetUrl = '/adminReview';
      }

      if (targetUrl) {
        btn.style.cursor = 'pointer';
        btn.addEventListener('click', () => {
          window.location.href = targetUrl;
        });
      }
    });
  }

  // ─────────────────────────────────────────────
  // SECTION 6 — Recent Activity "View" Links
  // ─────────────────────────────────────────────
  // Maps each activity item to its relevant admin page
  // based on the type of event described in the text.
  function initActivityLinks() {
    const activityItems = qsa('.activity-item');

    activityItems.forEach(item => {
      const viewLink = qs('a', item);
      const text     = item.textContent.toLowerCase();

      if (!viewLink) return;

      // Determine target based on activity text content
      if (text.includes('artisan') && text.includes('registration')) {
        viewLink.href = '/adminArtisanApproval';
        viewLink.title = 'Go to Artisan Approvals';
      } else if (text.includes('refund')) {
        viewLink.href = '/adminRefund';
        viewLink.title = 'Go to Refund Management';
      } else if (text.includes('order')) {
        viewLink.href = '/adminOrderManagment';
        viewLink.title = 'Go to Orders';
      } else if (text.includes('auction')) {
        viewLink.href = '/adminAuction';
        viewLink.title = 'Go to Auctions';
      } else if (text.includes('review') || text.includes('flagged') || text.includes('inappropriate')) {
        viewLink.href = '/adminReview';
        viewLink.title = 'Go to Review Moderation';
      } else {
        viewLink.href = '/adminReports';
        viewLink.title = 'Go to Reports';
      }
    });
  }

  // ─────────────────────────────────────────────
  // SECTION 7 — Orders per Week Chart Tooltips
  // ─────────────────────────────────────────────
  // Adds interactive hover tooltips to each data point
  // circle in the SVG line chart.
  function initOrdersChartTooltips() {
    const svg = qs('.bg-white svg');
    if (!svg) return;

    // Data labels matching the chart points (left → right)
    const weekLabels  = ['Week 1', 'Week 2', 'Week 3', 'Week 4', 'Week 5', 'Week 6'];
    const orderValues = window.chartData && window.chartData.ordersPerWeek ? window.chartData.ordersPerWeek : [85, 120, 160, 210, 260, 295];

    // Create a shared tooltip element
    const tooltip = createTooltip();

    const circles = svg.querySelectorAll('circle');
    circles.forEach((circle, i) => {
      circle.style.cursor = 'pointer';
      circle.setAttribute('tabindex', '0');
      circle.setAttribute('role', 'img');
      circle.setAttribute('aria-label', `${weekLabels[i]}: ${orderValues[i]} orders`);

      // Grow dot on hover
      circle.addEventListener('mouseenter', e => {
        circle.setAttribute('r', '8');
        circle.style.filter = 'drop-shadow(0 0 4px rgba(193,124,95,0.6))';
        showSvgTooltip(tooltip, e, `${weekLabels[i]}<br><strong>${orderValues[i]} orders</strong>`);
      });

      circle.addEventListener('mousemove', e => {
        moveSvgTooltip(tooltip, e);
      });

      circle.addEventListener('mouseleave', () => {
        circle.setAttribute('r', '5');
        circle.style.filter = '';
        hideTooltip(tooltip);
      });

      // Keyboard support
      circle.addEventListener('focus', e => {
        circle.setAttribute('r', '8');
        showSvgTooltip(tooltip, e, `${weekLabels[i]}: ${orderValues[i]} orders`);
      });
      circle.addEventListener('blur', () => {
        circle.setAttribute('r', '5');
        hideTooltip(tooltip);
      });
    });
  }

  // ─────────────────────────────────────────────
  // SECTION 8 — Revenue Bar Chart Hover
  // ─────────────────────────────────────────────
  // Adds hover highlight and tooltip to each revenue bar.
  function initRevenueChartTooltips() {
    // Target the second SVG (revenue chart)
    const svgs = qsa('.bg-white svg');
    const revSvg = svgs[1];
    if (!revSvg) return;

    const months  = window.chartData && window.chartData.monthLabels ? window.chartData.monthLabels : ['Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const revenueNumbers = window.chartData && window.chartData.revenuePerMonth ? window.chartData.revenuePerMonth : [8200, 12500, 15800, 19400, 24100, 28750];
    const revenues = revenueNumbers.map(n => 'BD ' + n.toLocaleString());

    const tooltip = createTooltip();
    const bars    = revSvg.querySelectorAll('rect');

    bars.forEach((bar, i) => {
      bar.style.cursor = 'pointer';
      const originalFill = bar.getAttribute('fill');

      bar.setAttribute('tabindex', '0');
      bar.setAttribute('role', 'img');
      bar.setAttribute('aria-label', `${months[i]}: ${revenues[i]}`);

      bar.addEventListener('mouseenter', e => {
        bar.setAttribute('fill', '#f59e0b'); // darker amber on hover
        bar.style.filter = 'drop-shadow(0 0 6px rgba(251,191,36,0.5))';
        showSvgTooltip(tooltip, e, `${months[i]}<br><strong>${revenues[i]}</strong>`);
      });

      bar.addEventListener('mousemove', e => moveSvgTooltip(tooltip, e));

      bar.addEventListener('mouseleave', () => {
        bar.setAttribute('fill', originalFill);
        bar.style.filter = '';
        hideTooltip(tooltip);
      });

      bar.addEventListener('focus', e => {
        bar.setAttribute('fill', '#f59e0b');
        showSvgTooltip(tooltip, e, `${months[i]}: ${revenues[i]}`);
      });
      bar.addEventListener('blur', () => {
        bar.setAttribute('fill', originalFill);
        hideTooltip(tooltip);
      });
    });
  }

  // ─────────────────────────────────────────────
  // SECTION 9 — Update Chart Visuals
  // ─────────────────────────────────────────────
  function updateChartsVisuals() {
    if (!window.chartData) return;

    // 1. Orders per week
    const lineSvg = qs('.bg-white svg');
    if (lineSvg && window.chartData.ordersPerWeek) {
        const orders = window.chartData.ordersPerWeek;
        const maxOrder = Math.max(...orders, 10);
        const getCY = val => 200 - (val / maxOrder) * 160;

        const cxValues = [0, 75, 225, 375, 525, 600];
        
        const pathArea = lineSvg.querySelectorAll('path')[0];
        const pathLine = lineSvg.querySelectorAll('path')[1];
        const circles = lineSvg.querySelectorAll('circle');
        
        let pathLineD = '';
        let pathAreaD = 'M 0,200 ';

        orders.forEach((val, i) => {
            if(i >= cxValues.length) return;
            const cx = cxValues[i];
            const cy = getCY(val);
            
            if (i === 0) {
                pathLineD += `M ${cx},${cy} `;
            } else {
                pathLineD += `L ${cx},${cy} `;
            }
            pathAreaD += `L ${cx},${cy} `;
            
            if (circles[i]) {
                circles[i].setAttribute('cy', cy);
            }
        });
        
        pathAreaD += `L 600,200 Z`;
        
        if (pathLine) pathLine.setAttribute('d', pathLineD.trim());
        if (pathArea) pathArea.setAttribute('d', pathAreaD.trim());

        const yAxisContainer = lineSvg.parentElement.querySelector('div:last-child');
        if (yAxisContainer && yAxisContainer.classList.contains('flex-col')) {
            yAxisContainer.innerHTML = `
                <span>${Math.round(maxOrder)}</span>
                <span>${Math.round(maxOrder * 0.66)}</span>
                <span>${Math.round(maxOrder * 0.33)}</span>
                <span>0</span>
            `;
        }
    }

    // 2. Revenue Overview
    const svgs = qsa('.bg-white svg');
    const revSvg = svgs[1];
    if (revSvg && window.chartData.revenuePerMonth) {
        const revenues = window.chartData.revenuePerMonth;
        const maxRev = Math.max(...revenues, 10);
        
        const yAxisTexts = revSvg.querySelectorAll('text');
        if (yAxisTexts.length >= 4) {
            yAxisTexts[0].textContent = Math.round(maxRev / 1000) + 'K BD';
            yAxisTexts[1].textContent = Math.round((maxRev * 0.66) / 1000) + 'K BD';
            yAxisTexts[2].textContent = Math.round((maxRev * 0.33) / 1000) + 'K BD';
            yAxisTexts[3].textContent = '0K BD';
        }

        const bars = revSvg.querySelectorAll('rect');
        revenues.forEach((val, i) => {
            if (bars[i]) {
                const height = (val / maxRev) * 150;
                const y = 190 - height;
                bars[i].setAttribute('height', height);
                bars[i].setAttribute('y', y);
            }
        });
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 10 — Tooltip Helpers
  // ─────────────────────────────────────────────
  function createTooltip() {
    const tip = document.createElement('div');
    tip.style.cssText = `
      position: fixed;
      background: #5c4a3d;
      color: #fff;
      padding: 6px 12px;
      border-radius: 8px;
      font-size: 12px;
      line-height: 1.5;
      pointer-events: none;
      z-index: 9999;
      display: none;
      white-space: nowrap;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    `;
    document.body.appendChild(tip);
    return tip;
  }

  function showSvgTooltip(tip, e, html) {
    tip.innerHTML = html;
    tip.style.display = 'block';
    moveSvgTooltip(tip, e);
  }

  function moveSvgTooltip(tip, e) {
    const x = (e.clientX || e.pageX) + 14;
    const y = (e.clientY || e.pageY) - 30;
    tip.style.left = `${x}px`;
    tip.style.top  = `${y}px`;
  }

  function hideTooltip(tip) {
    tip.style.display = 'none';
  }

  // ─────────────────────────────────────────────
  // SECTION 10 — Toast Notification System
  // ─────────────────────────────────────────────
  // Lightweight in-page toast for user feedback.
  // Types: 'success' | 'error' | 'warning' | 'info'
  function showToast(message, type = 'info', duration = 3000) {
    // Create container if it doesn't exist
    let container = qs('#toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.style.cssText = `
        position: fixed;
        top: 80px;
        right: 24px;
        z-index: 99999;
        display: flex;
        flex-direction: column;
        gap: 10px;
      `;
      document.body.appendChild(container);
    }

    const colors = {
      success : { bg: '#dcfce7', border: '#16a34a', text: '#15803d', icon: '✓' },
      error   : { bg: '#fee2e2', border: '#dc2626', text: '#b91c1c', icon: '✕' },
      warning : { bg: '#fef3c7', border: '#d97706', text: '#b45309', icon: '⚠' },
      info    : { bg: '#eff6ff', border: '#2563eb', text: '#1d4ed8', icon: 'ℹ' },
    };

    const c = colors[type] || colors.info;

    const toast = document.createElement('div');
    toast.style.cssText = `
      background: ${c.bg};
      border: 1px solid ${c.border};
      color: ${c.text};
      padding: 12px 16px;
      border-radius: 10px;
      font-size: 14px;
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 260px;
      max-width: 380px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.1);
      animation: slideInRight 0.3s ease;
      cursor: pointer;
    `;
    toast.innerHTML = `<span style="font-weight:bold;font-size:16px">${c.icon}</span><span>${message}</span>`;
    toast.title = 'Click to dismiss';

    // Add slide-in animation
    const style = document.createElement('style');
    style.textContent = `
      @keyframes slideInRight {
        from { transform: translateX(100%); opacity: 0; }
        to   { transform: translateX(0);   opacity: 1; }
      }
      @keyframes fadeOut {
        from { opacity: 1; transform: translateX(0); }
        to   { opacity: 0; transform: translateX(100%); }
      }
    `;
    if (!qs('#toast-style')) {
      style.id = 'toast-style';
      document.head.appendChild(style);
    }

    container.appendChild(toast);

    const dismiss = () => {
      toast.style.animation = 'fadeOut 0.3s ease forwards';
      setTimeout(() => toast.remove(), 300);
    };

    toast.addEventListener('click', dismiss);
    setTimeout(dismiss, duration);
  }

  // ─────────────────────────────────────────────
  // SECTION 11 — Stat Card Click Navigation
  // ─────────────────────────────────────────────
  // Clicking a KPI card navigates to its relevant admin page.
  function initStatCardNavigation() {
    const cardNavMap = [
      { label: 'Total Users',      url: '/adminUsersManagement'  },
      { label: 'Total Artisans',   url: '/adminArtisanApproval'  },
      { label: 'Active Products',  url: '/adminProducts'         },
      { label: 'Open Auctions',    url: '/adminAuction'          },
      { label: 'Pending Reviews',  url: '/adminReview'           },
      { label: 'Pending Refunds',  url: '/adminRefund'           },
    ];

    const statCards = qsa('.stat-card');

    statCards.forEach((card, i) => {
      const map = cardNavMap[i];
      if (!map) return;

      card.style.cursor = 'pointer';
      card.setAttribute('title', `Go to ${map.label}`);
      card.setAttribute('tabindex', '0');
      card.setAttribute('role', 'button');
      card.setAttribute('aria-label', `Navigate to ${map.label}`);

      const navigate = () => window.location.href = map.url;

      card.addEventListener('click', navigate);
      card.addEventListener('keydown', e => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          navigate();
        }
      });
    });
  }

  // ─────────────────────────────────────────────
  // SECTION 12 — API Fetch for Live KPI Stats
  // ─────────────────────────────────────────────
  // Fetches live numbers from the backend and updates
  // the KPI cards. Uncomment the call in the init block
  // once the API endpoint is ready.
  async function loadAdminStats() {
    try {
      const res = await fetch('/api/admin/stats', {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('adminToken') || ''}`,
          'Content-Type' : 'application/json',
        },
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();

      // Map API response fields to DOM element IDs
      const fieldMap = {
        totalUsers     : data.totalUsers,
        totalArtisans  : data.totalArtisans,
        activeProducts : data.activeProducts,
        openAuctions   : data.openAuctions,
        pendingReviews : data.pendingReviews,
        pendingRefunds : data.pendingRefunds,
      };

      // Update notification badge on bell icon
      const bellBadge = qs('nav .fa-bell + span');
      if (bellBadge && data.unreadNotifications !== undefined) {
        bellBadge.textContent = data.unreadNotifications;
      }

      // Update Quick Action badges
      const quickBadges = qsa('.quick-action-card span.rounded-full');
      if (quickBadges[0] && data.pendingArtisanApprovals !== undefined) {
        quickBadges[0].textContent = data.pendingArtisanApprovals;
      }
      if (quickBadges[1] && data.pendingRefunds !== undefined) {
        quickBadges[1].textContent = data.pendingRefunds;
      }
      if (quickBadges[2] && data.flaggedReviews !== undefined) {
        quickBadges[2].textContent = data.flaggedReviews;
      }

    } catch (err) {
      console.warn('[ArtsyVibe] Could not load admin stats from API:', err.message);
      // Silent fail — dashboard continues with static data
    }
  }

  // ─────────────────────────────────────────────
  // SECTION 13 — Notification Bell Badge Update
  // ─────────────────────────────────────────────
  // Polls for new notifications every 60 seconds.
  // Replace fetch URL with your actual endpoint.
  function initNotificationPolling() {
    const bellBadge = qs('nav .fa-bell ~ span, nav button span.bg-red-500');
    if (!bellBadge) return;

    const poll = async () => {
      try {
        const res  = await fetch('/api/admin/notifications/unread-count');
        if (!res.ok) return;
        const data = await res.json();
        if (data.count !== undefined) {
          bellBadge.textContent = data.count;
          bellBadge.style.display = data.count > 0 ? 'flex' : 'none';
        }
      } catch {
        // Silent fail — network may be unavailable
      }
    };

    // Uncomment to enable live polling:
    // poll();
    // setInterval(poll, 60_000);
  }

  // ─────────────────────────────────────────────
  // SECTION 14 — Page Load Animation
  // ─────────────────────────────────────────────
  // Staggered fade-in for the stat cards and activity items.
  function initPageAnimations() {
    const elements = [
      ...qsa('.stat-card'),
      ...qsa('.quick-action-card'),
      qs('.activity-item')?.closest('.bg-white'),
    ].filter(Boolean);

    elements.forEach((el, i) => {
      el.style.opacity   = '0';
      el.style.transform = 'translateY(20px)';
      el.style.transition = `opacity 0.4s ease ${i * 60}ms, transform 0.4s ease ${i * 60}ms`;

      // Trigger after a brief microtask delay
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          el.style.opacity   = '1';
          el.style.transform = 'translateY(0)';
        });
      });
    });
  }

  // ─────────────────────────────────────────────
  // INIT — Run everything on DOMContentLoaded
  // ─────────────────────────────────────────────
  document.addEventListener('DOMContentLoaded', () => {

    // Core navigation & UI
    setActiveSidebarLink();
    initUserMenu();
    initLogout();

    // Dashboard interactions
    initStatCardAnimations();
    initStatCardNavigation();
    initQuickActionButtons();
    initActivityLinks();

    // Chart interactivity
    updateChartsVisuals();
    initOrdersChartTooltips();
    initRevenueChartTooltips();

    // Page entrance animations
    initPageAnimations();

    // Notifications
    initNotificationPolling();

    // ── Live API stats (uncomment when backend is ready) ──
    // loadAdminStats();

  });

  // Expose showToast globally so other scripts / inline handlers can use it
  window.showToast = showToast;

})();
