// ============================================================
// my-wishlist.js — ArtsyVibe My Wishlist Page
// ============================================================

document.addEventListener('DOMContentLoaded', () => {

    // Login state elements
    const loginButtonWrapper = document.getElementById('login-button-wrapper');
    const userSection = document.getElementById('user-section');
    const navUserName = document.getElementById('nav-user-name');
    const navUserEmail = document.getElementById('nav-user-email');
    const notificationBadge = document.getElementById('notification-badge');
    const cartBadge = document.getElementById('cart-badge');

    function updateLoginState() {
        const loggedIn = sessionStorage.getItem('isLoggedIn') === 'true' || sessionStorage.getItem('loggedIn') === 'true';
        if (loggedIn) {
            loginButtonWrapper?.classList.add('hidden');
            userSection?.classList.remove('hidden');

            const userName = sessionStorage.getItem('userName') || 'John Doe';
            const userEmail = sessionStorage.getItem('userEmail') || 'john@example.com';
            if (navUserName) navUserName.textContent = userName;
            if (navUserEmail) navUserEmail.textContent = userEmail;

            updateNotificationBadge();
            loadWishlist(); // Load wishlist if logged in
        } else {
            loginButtonWrapper?.classList.remove('hidden');
            userSection?.classList.add('hidden');
        }

        updateCartBadge();
    }

    // ----------------------------------------------------------
    // 1. Category filter pills
    // ----------------------------------------------------------
    window.filterWishlist = function (category, button) {
        document.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
        button.classList.add('active');

        document.querySelectorAll('.wishlist-item').forEach(row => {
            const rowCategory = row.dataset.category ? row.dataset.category.toLowerCase() : '';
            const show = category === 'all' || rowCategory === category.toLowerCase();
            row.style.display = show ? '' : 'none';
        });
    };

    // ----------------------------------------------------------
    // 2. Remove from wishlist
    // ----------------------------------------------------------
    window.removeFromWishlist = async function (button) {
        const row = button.closest('.wishlist-item');
        if (!row) return;

        const productId = row.dataset.id;
        const userEmail = sessionStorage.getItem('userEmail');

        if (!userEmail) return;

        try {
            const response = await fetch(`/api/wishlist/remove/${productId}`, {
                method: 'DELETE',
                headers: {
                    'X-User-Email': userEmail
                }
            });

            if (response.ok) {
                row.style.opacity = '0';
                row.style.transform = 'translateX(-20px)';
                row.style.transition = 'all 0.3s ease';

                setTimeout(() => {
                    row.remove();
                    updateWishlistCount();
                    checkEmptyState();
                }, 300);
                showToast('Item removed from wishlist', 'success');
            } else {
                showToast('Failed to remove item', 'error');
            }
        } catch (error) {
            console.error('Error removing from wishlist:', error);
            showToast('An error occurred', 'error');
        }
    };

    // ----------------------------------------------------------
    // 3. Add to cart from wishlist
    // ----------------------------------------------------------
    window.addToCart = async function (button) {
        const row = button.closest('tr');
        const productId = row?.dataset?.id;
        const userEmail = sessionStorage.getItem('userEmail');
        const loggedIn = sessionStorage.getItem('isLoggedIn') === 'true' || sessionStorage.getItem('loggedIn') === 'true';

        if (!loggedIn || !userEmail) {
            showToast('Please login to add items to your cart', 'error');
            return;
        }
        if (!productId) return;

        const originalHTML = button.innerHTML;
        button.disabled = true;
        try {
            if (!window.addProductToCart) throw new Error('Cart unavailable');
            await window.addProductToCart(productId, 1);
            button.innerHTML = '<i class="fas fa-check mr-1"></i>Added!';
            button.classList.remove('bg-[#c17c5f]');
            button.classList.add('bg-green-600');
            showToast('Added to cart', 'success');
            setTimeout(() => {
                button.innerHTML = originalHTML;
                button.classList.remove('bg-green-600');
                button.classList.add('bg-[#c17c5f]');
                button.disabled = false;
            }, 1200);
        } catch (e) {
            showToast(e.message || 'Could not add to cart', 'error');
            button.disabled = false;
        }
    };

    // ----------------------------------------------------------
    // 4. "Add All to Cart" button (optional enhancement)
    // ----------------------------------------------------------
    buildAddAllButton();

    async function loadWishlist() {
        const userEmail = sessionStorage.getItem('userEmail');
        if (!userEmail) return;

        try {
            const response = await fetch('/api/wishlist', {
                headers: {
                    'X-User-Email': userEmail
                }
            });

            if (response.ok) {
                const products = await response.json();
                renderWishlist(products);
            }
        } catch (error) {
            console.error('Error loading wishlist:', error);
            updateWishlistCount();
            checkEmptyState();
        }
    }

    function renderWishlist(products) {
        const tbody = document.querySelector('tbody');
        if (!tbody) return;

        tbody.innerHTML = '';
        if (products.length === 0) {
            updateWishlistCount();
            checkEmptyState();
            return;
        }

        products.forEach(product => {
            const row = document.createElement('tr');
            row.className = 'wishlist-row border-b border-[#e5e0d8] wishlist-item';
            row.dataset.category = product.category;
            row.dataset.id = product.id;

            row.innerHTML = `
                <td class="py-4 px-6">
                    <div class="flex items-center gap-3">
                        <img src="${product.imageUrl}" alt="${product.title}" class="w-16 h-16 object-cover rounded-lg">
                        <div>
                            <p class="font-semibold text-[#5c4a3d]">${product.title}</p>
                            <p class="text-sm text-[#8b7355]">${product.description.substring(0, 50)}...</p>
                        </div>
                    </div>
                </td>
                <td class="py-4 px-6">
                    <p class="text-[#5c4a3d] font-medium">${product.artisanName || 'ArtsyVibe Artisan'}</p>
                </td>
                <td class="py-4 px-6">
                    <span class="text-[#8b7355]">${product.category}</span>
                </td>
                <td class="py-4 px-6">
                    <span class="font-bold text-[#c17c5f]">${product.price.toFixed(3)} BD</span>
                </td>
                <td class="py-4 px-6">
                    <div class="flex items-center gap-2">
                        <button class="add-to-cart-btn bg-[#c17c5f] hover:bg-[#a5664d] text-white px-4 py-2 rounded-lg font-medium text-sm transition-colors" onclick="addToCart(this)">
                            <i class="fas fa-shopping-cart mr-1"></i>Add to Cart
                        </button>
                        <button class="heart-btn active w-10 h-10 flex items-center justify-center text-[#ef4444]" onclick="removeFromWishlist(this)" title="Remove from wishlist">
                            <i class="fas fa-heart text-lg"></i>
                        </button>
                    </div>
                </td>
            `;
            tbody.appendChild(row);
        });

        updateWishlistCount();
        checkEmptyState();
    }

    // Initial load
    updateLoginState();
});

// ============================================================
// Wishlist count & empty state
// ============================================================

function updateWishlistCount() {
    const visible = document.querySelectorAll('.wishlist-item').length;
    const countEl = document.getElementById('wishlist-count');
    if (countEl) countEl.textContent = visible;
}

function checkEmptyState() {
    const remaining = document.querySelectorAll('.wishlist-item').length;
    const tableWrap = document.querySelector('table')?.closest('div');
    const filterBar = document.querySelector('.filter-pill')?.closest('.bg-white');
    const emptyState = document.getElementById('empty-state');

    if (remaining === 0) {
        tableWrap?.classList.add('hidden');
        filterBar?.classList.add('hidden');
        emptyState?.classList.remove('hidden');
    }
}

// ============================================================
// "Add All to Cart" button injection
// ============================================================

function buildAddAllButton() {
    const header = document.querySelector('main .mb-8');
    if (!header || document.getElementById('add-all-btn')) return;

    const btn = document.createElement('button');
    btn.id        = 'add-all-btn';
    btn.className = 'mt-4 px-6 py-2.5 bg-[#c17c5f] hover:bg-[#a5664d] text-white rounded-xl font-semibold transition-colors shadow-md text-sm';
    btn.innerHTML = '<i class="fas fa-shopping-cart mr-2"></i>Add All to Cart';
    header.appendChild(btn);

    btn.addEventListener('click', async () => {
        const rows = document.querySelectorAll('.wishlist-item:not([style*="none"])');
        const userEmail = sessionStorage.getItem('userEmail');
        const loggedIn = sessionStorage.getItem('isLoggedIn') === 'true' || sessionStorage.getItem('loggedIn') === 'true';
        if (!loggedIn || !userEmail) {
            showToast('Please login to add items to your cart', 'error');
            return;
        }
        if (rows.length === 0) {
            showToast('No items to add.', 'info');
            return;
        }
        if (!window.addProductToCart) {
            showToast('Cart unavailable', 'error');
            return;
        }
        btn.disabled = true;
        try {
            for (const row of rows) {
                const pid = row.dataset?.id;
                if (pid) await window.addProductToCart(pid, 1);
            }
            showToast(`${rows.length} item(s) added to cart!`, 'success');
        } catch (e) {
            showToast(e.message || 'Some items could not be added', 'error');
        } finally {
            btn.disabled = false;
        }
    });
}

// ============================================================
// Toast
// ============================================================

function showToast(message, type = 'info') {
    document.getElementById('av-toast')?.remove();
    const colours = { success: 'bg-green-600', error: 'bg-red-500', info: 'bg-[#c17c5f]' };
    const toast = document.createElement('div');
    toast.id        = 'av-toast';
    toast.className = `fixed bottom-6 right-6 z-50 px-6 py-3 rounded-xl text-white font-medium shadow-lg ${colours[type]}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, 3000);
}

// ============================================================
// Utility helpers (shared across pages via global scope)
// ============================================================

/**
 * Read the notification count from sessionStorage and update any badge on the page.
 */
function updateNotificationBadge() {
    const badge = document.getElementById('notification-badge');
    if (!badge) return;
    const count = parseInt(sessionStorage.getItem('notificationCount') ?? '0', 10);
    badge.textContent = count;
    badge.style.display = count > 0 ? 'flex' : 'none';
}

/**
 * Read cart item count from sessionStorage and update any cart badge on the page.
 */
function updateCartBadge() {
    document.querySelectorAll('.fa-shopping-cart')
        .forEach(icon => {
            const badge = icon.parentElement?.querySelector('span');
            if (!badge) return;
            const count = parseInt(sessionStorage.getItem('cartCount') ?? '0', 10);
            badge.textContent = count;
        });
}
