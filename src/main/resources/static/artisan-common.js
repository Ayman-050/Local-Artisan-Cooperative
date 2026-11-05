// ============================================================
// artisan-common.js: session, URL id, nav sync for artisan pages
// ============================================================

(function () {
    function isArtisanLoggedIn() {
        try {
            const loggedIn = sessionStorage.getItem('loggedIn') === 'true' ||
                sessionStorage.getItem('isLoggedIn') === 'true';
            const role = (sessionStorage.getItem('userRole') || '').toUpperCase();
            return loggedIn && role === 'ARTISAN' && getArtisanUserId() !== null;
        } catch (e) {
            return false;
        }
    }

    function getArtisanUserId() {
        const id = sessionStorage.getItem('userId');
        if (!id || id === 'undefined' || id === 'null') {
            return null;
        }
        return id;
    }

    function appendIdToHref(href, userId) {
        if (!href || href === '#' || href.includes('javascript:')) return href;
        try {
            const url = new URL(href, window.location.origin);
            if (url.pathname.startsWith('/artisan')) {
                url.searchParams.set('id', userId);
                return url.pathname + url.search;
            }
        } catch (e) {
            // ignore
        }
        return href;
    }

    function syncArtisanNavLinks(userId) {
        if (!userId) return;
        document.querySelectorAll('a[href^="/artisan"]').forEach((link) => {
            const href = link.getAttribute('href');
            if (!href) return;
            link.setAttribute('href', appendIdToHref(href.split('?')[0], userId));
        });
    }

    function syncArtisanProfileFromApi() {
        const email = sessionStorage.getItem('userEmail');
        if (!email) return;

        fetch(`/api/user/me?email=${encodeURIComponent(email)}`)
            .then((r) => r.json())
            .then((data) => {
                if (!data.success || !data.user) return;
                sessionStorage.setItem('userProfile', JSON.stringify(data.user));
                sessionStorage.setItem('userName', data.user.name || '');
                sessionStorage.setItem('userId', String(data.user.userId));

                const profilePicture = data.user.profilePicture ||
                    'https://cdn-icons-png.flaticon.com/512/149/149071.png';

                document.querySelectorAll('aside img.rounded-full, .user-menu img.rounded-full, #artisanProfileImg').forEach((img) => {
                    img.src = profilePicture;
                    if (data.user.name) img.alt = data.user.name;
                });

                document.querySelectorAll('aside h3.text-white').forEach((el) => {
                    el.textContent = data.user.name || 'Artisan';
                });

                const shopNameEl = document.querySelector('aside .border-b p.text-sm');
                if (shopNameEl) {
                    shopNameEl.textContent = data.user.shopName || '';
                }

                document.querySelectorAll('.user-menu p.font-semibold.text-\\[\\#5c4a3d\\]').forEach((el) => {
                    el.textContent = data.user.name || 'Artisan';
                });

                document.querySelectorAll('.user-menu p.text-xs.text-\\[\\#8b7355\\]').forEach((el) => {
                    el.textContent = data.user.email || '';
                });
            })
            .catch((err) => console.error('Artisan profile sync failed', err));
    }

    window.initArtisanPage = function initArtisanPage() {
        if (!isArtisanLoggedIn()) {
            try {
                sessionStorage.setItem('postLoginNext', window.location.pathname + window.location.search);
            } catch (e) {
                // ignore
            }
            window.location.href = '/login';
            return;
        }

        const userId = getArtisanUserId();
        if (!userId) {
            window.location.href = '/login';
            return;
        }

        const url = new URL(window.location.href);
        const urlId = url.searchParams.get('id');

        if (!urlId || urlId !== userId) {
            url.searchParams.set('id', userId);
            window.location.replace(url.toString());
            return;
        }

        syncArtisanNavLinks(userId);
        syncArtisanProfileFromApi();
        syncUnreadNotificationsBadge(userId);
        setInterval(() => syncUnreadNotificationsBadge(userId), 10000);
        setupUserMenuDropdown();
    };

    function setupUserMenuDropdown() {
        const userMenu = document.querySelector('.user-menu');
        const userMenuButton = document.getElementById('userMenuButton');
        const userMenuDropdown = document.getElementById('userMenuDropdown');
        const chevronIcon = document.getElementById('chevronIcon');
        const logoutButton = document.getElementById('logoutButton');

        if (!userMenu || userMenu.dataset.dropdownWired === 'true') return;
        userMenu.dataset.dropdownWired = 'true';

        let hideTimeout;

        function showUserMenu() {
            clearTimeout(hideTimeout);
            userMenuDropdown?.classList.add('show');
            chevronIcon?.classList.add('rotated');
        }

        function hideUserMenu() {
            hideTimeout = setTimeout(() => {
                userMenuDropdown?.classList.remove('show');
                chevronIcon?.classList.remove('rotated');
            }, 150);
        }

        userMenu.addEventListener('mouseenter', showUserMenu);
        userMenu.addEventListener('mouseleave', hideUserMenu);

        userMenuButton?.addEventListener('click', (e) => {
            e.stopPropagation();
            const isOpen = userMenuDropdown?.classList.contains('show');
            if (isOpen) {
                hideUserMenu();
            } else {
                showUserMenu();
            }
        });

        document.addEventListener('click', (e) => {
            if (!userMenu.contains(e.target)) {
                userMenuDropdown?.classList.remove('show');
                chevronIcon?.classList.remove('rotated');
            }
        });

        if (logoutButton) {
            const newLogoutButton = logoutButton.cloneNode(true);
            logoutButton.parentNode.replaceChild(newLogoutButton, logoutButton);
            newLogoutButton.addEventListener('click', (e) => {
                e.preventDefault();
                if (window.confirm('Are you sure you want to logout?')) {
                    window.artisanLogout();
                }
            });
        }
    }

    function syncUnreadNotificationsBadge(userId) {
        if (!userId) return;
        fetch(`/api/notifications/${userId}/unread-count`)
            .then(r => r.json())
            .then(count => {
                const link = document.getElementById('notifications-link');
                if (!link) return;
                
                 let badge = document.getElementById('header-notification-badge') || link.querySelector('.bg-red-500, #notification-badge, span.absolute');
                 if (!badge) {
                     badge = document.createElement('span');
                     badge.id = 'header-notification-badge';
                     badge.className = 'absolute top-1 right-1 w-2.5 h-2.5 bg-red-500 rounded-full border-2 border-[#faf9f6] hidden';
                     const btn = link.querySelector('button');
                     if (btn) btn.appendChild(badge);
                     else link.appendChild(badge);
                 }
                 
                 if (count > 0) {
                     badge.classList.remove('hidden');
                 } else {
                     badge.classList.add('hidden');
                 }
            })
            .catch(err => console.error('Failed to sync notification count', err));
    }

    window.artisanLogout = function artisanLogout() {
        const email = sessionStorage.getItem('userEmail');
        const doLogout = () => {
            sessionStorage.removeItem('loggedIn');
            sessionStorage.removeItem('isLoggedIn');
            sessionStorage.removeItem('userEmail');
            sessionStorage.removeItem('userName');
            sessionStorage.removeItem('userRole');
            sessionStorage.removeItem('userId');
            sessionStorage.removeItem('userProfile');
            sessionStorage.removeItem('postLoginNext');
            window.location.href = '/login';
        };
        if (email) {
            fetch('/api/auth/logout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: email })
            }).finally(doLogout);
        } else {
            doLogout();
        }
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', window.initArtisanPage);
    } else {
        window.initArtisanPage();
    }
})();
