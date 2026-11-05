(function () {
  function getLoggedIn() {
    try {
      return sessionStorage.getItem('loggedIn') === 'true';
    } catch (e) {
      return false;
    }
  }

  function normalizeRedirectTo(redirectTo) {
    if (!redirectTo) return '/login';
    // allow full URLs or relative paths
    return redirectTo;
  }

  function getNextFromUrl() {
    const url = new URL(window.location.href);
    return url.searchParams.get('next');
  }

  function setNextIfMissing(next) {
    try {
      if (!sessionStorage.getItem('postLoginNext') && next) {
        sessionStorage.setItem('postLoginNext', next);
      }
    } catch (e) {
      // ignore
    }
  }

  function redirectToLogin({ redirectTo, next }) {
    const loginPath = normalizeRedirectTo(redirectTo);
    const params = new URLSearchParams();
    if (next) params.set('next', next);
    // Clear message param if present? keep it simple.
    window.location.href = params.toString()
      ? `${loginPath}?${params.toString()}`
      : loginPath;
  }

  function showLockedOverlay(message) {
    // Replace body content to block all clicks/JS-driven actions.
    document.documentElement.innerHTML = `
      <head>
        <meta charset="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <title>Login Required</title>
        <script src="https://cdn.tailwindcss.com"></script>
      </head>
      <body class="min-h-screen bg-[#faf9f6] flex items-center justify-center">
        <div class="max-w-xl w-full mx-4">
          <div class="bg-white rounded-2xl shadow-lg p-8 border border-[#e5e0d8]">
            <div class="text-[#c17c5f] text-4xl mb-4">🔒</div>
            <h1 class="text-2xl font-bold text-[#5c4a3d] mb-2">Login / Register Required</h1>
            <p class="text-[#8b7355] text-lg mb-6">${message || 'You need to login or register first.'}</p>
            <div class="flex flex-col sm:flex-row gap-3">
              <a href="/login" class="inline-flex items-center justify-center px-6 py-3 rounded-xl bg-[#c17c5f] text-white font-semibold hover:bg-[#a5664d] transition-colors">Go to Login</a>
              <button id="auth-guard-redirect" class="inline-flex items-center justify-center px-6 py-3 rounded-xl border-2 border-[#c17c5f] text-[#c17c5f] font-semibold hover:bg-[#f5ebe0] transition-colors">Continue</button>
            </div>
            <p class="text-sm text-[#8b7355] mt-4">You will be redirected to login automatically.</p>
          </div>
        </div>
      </body>
    `;

    const btn = document.getElementById('auth-guard-redirect');
    if (btn) {
      btn.addEventListener('click', function () {
        // No-op; actual redirect handled by setTimeout below.
      });
    }
  }

  function showActionBlockedToast(message) {
    if (document.getElementById('auth-guard-action-toast')) return;

    const toast = document.createElement('div');
    toast.id = 'auth-guard-action-toast';
    toast.style.position = 'fixed';
    toast.style.bottom = '20px';
    toast.style.right = '20px';
    toast.style.zIndex = '9999';
    toast.style.padding = '14px 20px';
    toast.style.borderRadius = '16px';
    toast.style.backgroundColor = 'rgba(34, 197, 94, 0.95)';
    toast.style.color = '#ffffff';
    toast.style.fontFamily = 'Inter, sans-serif';
    toast.style.boxShadow = '0 12px 30px rgba(0,0,0,0.18)';
    toast.style.maxWidth = '320px';
    toast.textContent = message || 'Please login or register first to perform this action.';

    document.body.appendChild(toast);
    setTimeout(function () {
      toast.style.opacity = '0';
      setTimeout(function () { toast.remove(); }, 250);
    }, 2800);
  }

  window.isUserAuthenticated = function () {
    return getLoggedIn();
  };

  window.requireLoginForAction = function (message) {
    if (getLoggedIn()) return true;
    showActionBlockedToast(message || 'Please login or register first to perform this action.');
    return false;
  };

  window.initAuthGuard = function initAuthGuard(options) {
    const opts = options || {};
    const redirectTo = opts.redirectTo || '/login';
    const message = opts.message || 'Please login or register first to access this page.';

    // Allow login page / public assets.
    if (getLoggedIn()) return;

    // Preserve attempted path for after login.
    // Use exact pathname + search.
    const next = opts.next || (window.location.pathname + window.location.search);
    setNextIfMissing(getNextFromUrl() || next);

    const intendedNext = getNextFromUrl() || next;

    showLockedOverlay(message);

    // Redirect after overlay renders.
    setTimeout(function () {
      redirectToLogin({ redirectTo, next: intendedNext });
    }, 1200);
  };

  window.updateNavAuthState = function updateNavAuthState() {
    const loginWrapper = document.getElementById('login-button-wrapper');
    const userSection = document.getElementById('user-section');
    const userName = document.getElementById('nav-user-name');
    const userEmail = document.getElementById('nav-user-email');
    const userMenuBtn = document.getElementById('user-menu-button') || document.getElementById('userMenuButton') || document.querySelector('a[href="/profile"]');
    const loggedIn = getLoggedIn();
    const storedEmail = sessionStorage.getItem('userEmail');
    const userRole = (sessionStorage.getItem('userRole') || '').toUpperCase();

    // Protection for admin pages
    const isUnderAdminPath = window.location.pathname.toLowerCase().includes('admin');
    if (isUnderAdminPath && userRole !== 'ADMIN') {
        console.warn("Unauthorized admin access attempt.");
        sessionStorage.clear();
        window.location.replace('/login?message=Unauthorized');
        return;
    }

    if (loggedIn && storedEmail) {
      // Sync with server in background
      fetch(`/api/user/me?email=${encodeURIComponent(storedEmail)}`)
        .then(r => r.json())
        .then(data => {
          if (data.success) {
            if (data.user.status === 'suspended') {
              sessionStorage.clear();
              localStorage.clear();
              window.location.replace('/login?message=Suspended');
              return;
            }
            sessionStorage.setItem('userProfile', JSON.stringify(data.user));
            sessionStorage.setItem('userName', data.user.name);
            applyNavState(data.user);
            updateAdminProfileWidgets(data.user);
            window.syncNotificationCountFromServer();
            updateCartBadge();
          }
        })
        .catch(err => console.error("Nav sync failed", err));

      // Initial apply from session storage
      const userProfileStr = sessionStorage.getItem('userProfile');
      if (userProfileStr) {
        try {
          const profile = JSON.parse(userProfileStr);
          applyNavState(profile);
          updateAdminProfileWidgets(profile);
        } catch(e) {}
      }
    } else {
      if (loginWrapper) loginWrapper.classList.remove('hidden');
      if (userSection) userSection.classList.add('hidden');
    }

    function applyNavState(profile) {
      if (loginWrapper) loginWrapper.classList.add('hidden');
      if (userSection) userSection.classList.remove('hidden');
      if (userName) userName.textContent = profile.name || 'Profile';
      if (userEmail) userEmail.textContent = profile.email || '';

      if (userMenuBtn) {
        const profileIcon = userMenuBtn.querySelector('i.fas.fa-user-circle');
        let profileImg = userMenuBtn.querySelector('img.nav-profile-img');
        const profilePicture = profile.profilePicture || 'https://cdn-icons-png.flaticon.com/512/149/149071.png';

        if (profileIcon) {
          const img = document.createElement('img');
          img.src = profilePicture;
          img.className = 'w-8 h-8 rounded-full object-cover border border-[#c17c5f] nav-profile-img';
          profileIcon.replaceWith(img);
        } else if (profileImg) {
          profileImg.src = profilePicture;
        } else {
            // Check if the button itself is an image or contains one
            const innerImg = userMenuBtn.querySelector('img');
            if (innerImg) innerImg.src = profilePicture;
        }
      }
    }

    function updateAdminProfileWidgets(profile) {
      const profilePicture = profile.profilePicture || 'https://cdn-icons-png.flaticon.com/512/149/149071.png';
      const profileName = profile.name || 'Admin User';
      const profileEmail = profile.email || 'admin@artisancoop.com';

      const headerAdminImg = document.querySelector('.user-menu button img');
      if (headerAdminImg && headerAdminImg.alt === 'Admin User') {
        headerAdminImg.src = profilePicture;
      }

      const sidebarAdminImg = document.querySelector('aside .flex.items-center > img.rounded-full');
      if (sidebarAdminImg && sidebarAdminImg.alt === 'Admin User') {
        sidebarAdminImg.src = profilePicture;
      }

      const adminHeaderName = document.querySelector('.user-menu .font-semibold');
      const adminHeaderEmail = document.querySelector('.user-menu .text-xs');
      const adminSidebarName = document.querySelector('aside .flex.items-center h3');

      if (adminHeaderName && adminHeaderName.textContent.trim() === 'Admin User') {
        adminHeaderName.textContent = profileName;
      }
      if (adminHeaderEmail && adminHeaderEmail.textContent.trim() === 'admin@artisancoop.com') {
        adminHeaderEmail.textContent = profileEmail;
      }
      if (adminSidebarName && adminSidebarName.textContent.trim() === 'Admin User') {
        adminSidebarName.textContent = profileName;
      }
    }
  };

  window.updateNotificationBadge = function updateNotificationBadge() {
    const badge = document.getElementById('notification-badge');
    if (!badge) return;
    const count = parseInt(sessionStorage.getItem('notificationCount') ?? '0', 10);
    badge.textContent = count;
    badge.style.display = count > 0 ? 'flex' : 'none';
  };

  /**
   * Sync unread notification count from the database. Safe to call when logged out (no-op).
   */
  window.syncNotificationCountFromServer = async function syncNotificationCountFromServer() {
    try {
      const userId = sessionStorage.getItem('userId');
      const loggedIn = sessionStorage.getItem('loggedIn') === 'true' || sessionStorage.getItem('isLoggedIn') === 'true';
      if (!userId || !loggedIn) {
        sessionStorage.setItem('notificationCount', '0');
        window.updateNotificationBadge();
        return 0;
      }
      const res = await fetch(`/api/notifications/${userId}/unread-count`);
      if (!res.ok) {
        sessionStorage.setItem('notificationCount', '0');
        window.updateNotificationBadge();
        return 0;
      }
      const count = await res.json();
      const n = typeof count === 'number' ? count : 0;
      sessionStorage.setItem('notificationCount', String(n));
      window.updateNotificationBadge();
      return n;
    } catch (e) {
      console.warn('syncNotificationCountFromServer failed', e);
      return parseInt(sessionStorage.getItem('notificationCount') ?? '0', 10);
    }
  };

  let notificationPollInterval = null;

  function startNotificationPolling() {
    if (notificationPollInterval || !getLoggedIn()) return;
    notificationPollInterval = setInterval(function () {
      if (!getLoggedIn()) {
        clearInterval(notificationPollInterval);
        notificationPollInterval = null;
        return;
      }
      window.syncNotificationCountFromServer();
    }, 30000);
  }

  window.updateCartBadge = function updateCartBadge() {
    document.querySelectorAll('.fa-shopping-cart').forEach(icon => {
      const badge = icon.parentElement?.querySelector('span');
      if (!badge) return;
      const count = parseInt(sessionStorage.getItem('cartCount') ?? '0', 10);
      badge.textContent = count;
      badge.style.display = count > 0 ? 'flex' : 'none';
    });
  };

  /**
   * Sync cart item count from the database (open cart order). Safe to call when logged out (no-op).
   */
  window.syncCartCountFromServer = async function syncCartCountFromServer() {
    try {
      const email = sessionStorage.getItem('userEmail');
      const loggedIn = sessionStorage.getItem('loggedIn') === 'true' || sessionStorage.getItem('isLoggedIn') === 'true';
      if (!email || !loggedIn) {
        sessionStorage.setItem('cartCount', '0');
        window.updateCartBadge();
        return 0;
      }
      const res = await fetch('/api/cart', {
        headers: { 'X-User-Email': email }
      });
      if (!res.ok) {
        sessionStorage.setItem('cartCount', '0');
        window.updateCartBadge();
        return 0;
      }
      const data = await res.json();
      const n = typeof data.itemCount === 'number' ? data.itemCount : 0;
      sessionStorage.setItem('cartCount', String(n));
      window.updateCartBadge();
      return n;
    } catch (e) {
      console.warn('syncCartCountFromServer failed', e);
      return parseInt(sessionStorage.getItem('cartCount') ?? '0', 10);
    }
  };

  /**
   * Add a standard (non-auction) product to the customer's cart via POST /api/cart/add.
   */
  window.addProductToCart = async function addProductToCart(productId, quantity) {
    const email = sessionStorage.getItem('userEmail');
    if (!email) {
      throw new Error('Not logged in');
    }
    const res = await fetch('/api/cart/add', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Email': email
      },
      body: JSON.stringify({
        productId: Number(productId),
        quantity: quantity != null ? Number(quantity) : 1
      })
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(data.message || 'Could not add to cart');
    }
    const n = typeof data.itemCount === 'number' ? data.itemCount : 0;
    sessionStorage.setItem('cartCount', String(n));
    window.updateCartBadge();
    return data;
  };

  function runAuthGuardInit() {
    window.updateNavAuthState();

    // Role-based redirection for logged-in users on general pages
    const loggedIn = getLoggedIn();
    if (loggedIn) {
        const userRole = (sessionStorage.getItem('userRole') || '').toUpperCase();
        const path = window.location.pathname.toLowerCase();

        // 1. Admin redirection: if logged in as Admin but on a non-admin page
        if (userRole === 'ADMIN') {
            const isAdminPage = path.includes('admin');
            const isLoginPage = path.includes('login');
            // If an Admin is on a public page (like /index), force them back to the dashboard.
            if (!isAdminPage && !isLoginPage) {
                window.location.replace('/adminDashboard');
                return;
            }
        }

        // 2. Artisan redirection: if on home or login pages
        if (userRole === 'ARTISAN') {
             const isPublicHome = path === '/' || path === '/index' || path === '/index.html' || path === '' || path === '/login';
             if (isPublicHome) {
                 // Try to get ID from profile if needed, or just generic dashboard
                 const userProfileStr = sessionStorage.getItem('userProfile');
                 let artisanId = '';
                 if (userProfileStr) {
                     try {
                         const profile = JSON.parse(userProfileStr);
                         artisanId = profile.userId || '';
                     } catch(e) {}
                 }
                 window.location.replace(artisanId ? `/artisanDashboard?id=${artisanId}` : '/artisanDashboard');
                 return;
             }
        }
    }

    window.updateNotificationBadge();
    window.updateCartBadge();
    window.syncCartCountFromServer();
    window.syncNotificationCountFromServer();
    startNotificationPolling();
  }

  // Capture site exit / pagehide activity tracking
  window.addEventListener('pagehide', () => {
    const email = sessionStorage.getItem('userEmail');
    if (email) {
      fetch('/api/auth/logout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email }),
        keepalive: true
      });
    }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', runAuthGuardInit);
  } else {
    runAuthGuardInit();
  }
})();