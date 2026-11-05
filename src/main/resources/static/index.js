// ============================================================
// index.js — ArtsyVibe Home Page
// ============================================================

document.addEventListener('DOMContentLoaded', () => {

    // ----------------------------------------------------------
    // 1. Search functionality
    // ----------------------------------------------------------
    const heroSearchWrapper = document.querySelector('.hero-gradient .relative');
    const searchInput = heroSearchWrapper?.querySelector('input[type="text"]');
    const searchButton = heroSearchWrapper?.querySelector('button');

    const loginButtonWrapper = document.getElementById('login-button-wrapper');
    const userSection = document.getElementById('user-section');
    const navUserName = document.getElementById('nav-user-name');
    const navUserEmail = document.getElementById('nav-user-email');
    const notificationBadge = document.getElementById('notification-badge');
    const cartBadge = document.getElementById('cart-badge');

    function handleSearch() {
        const query = searchInput?.value.trim();
        if (!query) return;
        // Navigate to products page with query parameter
        window.location.href = `/products?search=${encodeURIComponent(query)}`;
    }

    // Removed redundant updateLoginState - handled globally by authguard.js

    searchButton?.addEventListener('click', handleSearch);

    searchInput?.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') handleSearch();
    });

    // ----------------------------------------------------------
    // 2. Category cards — navigate to products page filtered by category
    // ----------------------------------------------------------
    const categoryCards = document.querySelectorAll('.category-card');
    categoryCards.forEach(card => {
        card.addEventListener('click', () => {
            const category = card.querySelector('h3')?.textContent.trim().toLowerCase();
            window.location.href = `/products?category=${encodeURIComponent(category)}`;
        });
        // Keyboard accessibility
        card.setAttribute('tabindex', '0');
        card.setAttribute('role', 'button');
        card.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') card.click();
        });
    });

    // ----------------------------------------------------------
    // 3. Featured product "View Details" buttons
    // ----------------------------------------------------------
    const viewDetailsBtns = document.querySelectorAll('.product-card button');
    viewDetailsBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const productName = btn.closest('.product-card')
                                   ?.querySelector('h3')?.textContent.trim();
            // In a real app this would use a product ID from data attributes.
            // For now we navigate to a generic product detail page.
            console.log('View details for:', productName);
            window.location.href = '/ProductDetailsStandard';
        });
    });

    // ----------------------------------------------------------
    // 4. Newsletter subscription (footer)
    // ----------------------------------------------------------
    const newsletterBtn = document.querySelector('footer button');
    const newsletterInput = document.querySelector('footer input[type="email"]');

    newsletterBtn?.addEventListener('click', () => {
        const email = newsletterInput?.value.trim();
        if (!email || !isValidEmail(email)) {
            showToast('Please enter a valid email address.', 'error');
            return;
        }
        // Simulate subscription
        showToast('Thanks for subscribing!', 'success');
        if (newsletterInput) newsletterInput.value = '';
    });

    newsletterInput?.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') newsletterBtn?.click();
    });

    // 6. Badges initialization (Login state handled by authguard.js)
    // ----------------------------------------------------------
    if (window.updateNotificationBadge) updateNotificationBadge();
    if (window.updateCartBadge) updateCartBadge();
});

// ============================================================
// Utility helpers (shared across pages via global scope)
// ============================================================

/**
 * Basic email validation.
 * @param {string} email
 * @returns {boolean}
 */
function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

/**
 * Show a lightweight toast notification.
 * @param {string} message
 * @param {'success'|'error'|'info'} type
 */
function showToast(message, type = 'info') {
    // Remove existing toast if present
    document.getElementById('av-toast')?.remove();

    const colours = {
        success: 'bg-green-600',
        error:   'bg-red-500',
        info:    'bg-[#c17c5f]',
    };

    const toast = document.createElement('div');
    toast.id = 'av-toast';
    toast.className = `fixed bottom-6 right-6 z-50 px-6 py-3 rounded-xl text-white font-medium shadow-lg
                       transition-all duration-300 ${colours[type] ?? colours.info}`;
    toast.textContent = message;
    document.body.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

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