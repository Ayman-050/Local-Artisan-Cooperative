// ============================================================
// auctions.js — ArtsyVibe Auctions Page
// ============================================================

// Shared filter state — single source of truth
let _activeTab      = 'all';   // Default: show all auctions
let _activeCategory = 'all';

document.addEventListener('DOMContentLoaded', () => {

    // ----------------------------------------------------------
    // 1. Price/bid range slider
    // ----------------------------------------------------------
    const priceRange = document.getElementById('priceRange');
    const priceValue = document.getElementById('priceValue');

    // Login state elements
    const loginButtonWrapper = document.getElementById('login-button-wrapper');
    const userSection = document.getElementById('user-section');
    const navUserName = document.getElementById('nav-user-name');
    const navUserEmail = document.getElementById('nav-user-email');
    const notificationBadge = document.getElementById('notification-badge');
    const cartBadge = document.getElementById('cart-badge');

    // Removed redundant updateLoginState - handled globally by authguard.js

    // Min/max bid filter; the upper bound covers the highest current bid shown.
    const bidValues = [...document.querySelectorAll('.auction-card .auction-bid-amount')]
        .map(el => parseFloat(el.textContent.replace(/[^0-9.]/g, '')) || 0);
    const priceControlEl = document.getElementById('priceRangeControl');
    if (priceControlEl && window.PriceRange) {
        window.auctionPriceRange = PriceRange.create(priceControlEl, {
            max: Math.ceil(Math.max(0, ...bidValues) / 10) * 10 || 1000,
            onChange: () => applyFilters()
        });
    }

    const requireLoginToAct = (message) => window.requireLoginForAction ? window.requireLoginForAction(message) : true;

    // ----------------------------------------------------------
    // 2. Countdown timers
    // ----------------------------------------------------------
    startCountdowns();
    const countdownInterval = setInterval(tickCountdowns, 1000);

    // ----------------------------------------------------------
    // 3. Tab switching (Live Now / Upcoming / Ended / All)
    // ----------------------------------------------------------
    window.switchTab = function (button, tabType) {
        if (_activeTab === tabType) {
            _activeTab = 'all';
        } else {
            _activeTab = tabType;
        }

        updateTabButtons(_activeTab, button);
        updateAuctionHeader(_activeTab);
        applyFilters();
    };

    function updateTabButtons(tabType, clickedButton) {
        document.querySelectorAll('.tab-pill').forEach(t => t.classList.remove('active'));
        if (tabType === 'all') {
            const allTab = document.querySelector('.tab-pill[data-tab="all"]');
            if (allTab) allTab.classList.add('active');
            return;
        }
        if (clickedButton) {
            clickedButton.classList.add('active');
            return;
        }
        const activeTab = document.querySelector(`.tab-pill[data-tab="${tabType}"]`);
        if (activeTab) activeTab.classList.add('active');
    }

    function updateAuctionHeader(tabType) {
        const header = document.getElementById('auction-page-header');
        const subtext = document.getElementById('auction-page-subtext');
        if (!header || !subtext) return;

        if (tabType === 'live') {
            header.textContent = 'Live Auctions';
            subtext.textContent = 'Bid on items currently available in live bidding.';
        } else if (tabType === 'upcoming') {
            header.textContent = 'Upcoming Auctions';
            subtext.textContent = 'See the next unique handcrafted items arriving soon.';
        } else if (tabType === 'ended') {
            header.textContent = 'Ended Auctions';
            subtext.textContent = 'Review completed auctions and sold treasures.';
        } else {
            header.textContent = 'All Auctions';
            subtext.textContent = 'Browse live, upcoming, and ended handcrafted auctions.';
        }
    }

    // ----------------------------------------------------------
    // 4. Category filter pills
    // ----------------------------------------------------------
    window.filterCategory = function (button, category) {
        document.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
        button.classList.add('active');
        _activeCategory = category;
        applyFilters();
    };

    // ----------------------------------------------------------
    // 5. Sort dropdown
    // ----------------------------------------------------------
    const sortSelect = document.querySelector('select');
    sortSelect?.addEventListener('change', () => sortAuctions(sortSelect.value));

    // ----------------------------------------------------------
    // 6. Custom checkboxes in sidebar
    // ----------------------------------------------------------
    document.querySelectorAll('.custom-checkbox').forEach(checkbox => {
        checkbox.addEventListener('change', function () {
            const icon   = this.parentElement.querySelector('.checkbox-icon');
            const visual = this.parentElement.querySelector('div');
            if (this.checked) {
                if (icon) icon.style.opacity = '1';
                visual?.classList.add('bg-[#c17c5f]', 'border-[#c17c5f]');
            } else {
                if (icon) icon.style.opacity = '0';
                visual?.classList.remove('bg-[#c17c5f]', 'border-[#c17c5f]');
            }
            applyFilters();
        });
    });

    // ----------------------------------------------------------
    // 7. Sidebar category radios
    // ----------------------------------------------------------
    document.querySelectorAll('input[name="category"]').forEach(radio => {
        radio.addEventListener('change', () => applyFilters());
    });

    // ----------------------------------------------------------
    // 8. Sidebar search input
    // ----------------------------------------------------------
    const sidebarSearch = document.querySelector('aside input[type="text"]');
    sidebarSearch?.addEventListener('input', () => applyFilters());

    // ----------------------------------------------------------
    // 9. "Place Bid" and "View Details" buttons
    //    — clicking either navigates to the product detail page
    // ----------------------------------------------------------
    document.getElementById('auction-grid')?.addEventListener('click', (e) => {
        const link = e.target.closest('a');
        if (link && link.textContent.trim() === 'View Details') {
            return;
        }

        const btn = e.target.closest('button');
        if (!btn) return;

        const btnText = btn.textContent.trim();

        if (btnText === 'Place Bid') {
            if (!requireLoginToAct('Login/Register first to place a bid.')) return;
            navigateToProductPage(btn);
            return;
        }

        if (btnText === 'Notify Me') {
            if (!requireLoginToAct('Login/Register first to be notified about this auction.')) return;
            btn.dataset.notifyState = 'notified';
            btn.dataset.originalHtml = btn.innerHTML;
            btn.dataset.originalClass = btn.className;
            btn.innerHTML = '<i class="fas fa-check mr-2"></i>Notified';
            btn.classList.add('bg-[#c17c5f]', 'text-white', 'notified');
            btn.classList.remove('border-[#c17c5f]', 'text-[#c17c5f]');
            btn.title = 'Click to cancel notification';
            showToast('You will be notified when this auction starts!', 'success');
            return;
        }

        if (btn.dataset.notifyState === 'notified') {
            btn.dataset.notifyState = 'canceled';
            btn.innerHTML = 'Notify Me';
            btn.className = btn.dataset.originalClass || 'w-full py-2.5 border-2 border-[#c17c5f] text-[#c17c5f] hover:bg-[#c17c5f] hover:text-white rounded-lg font-semibold transition-colors';
            btn.style.backgroundColor = '';
            btn.style.borderColor = '';
            btn.style.color = '';
            delete btn.dataset.hoverHtml;
            btn.title = 'Notify me when this auction starts';
            showToast('Notification canceled.', 'info');
        }
    });

    document.getElementById('auction-grid')?.addEventListener('mouseover', (e) => {
        const btn = e.target.closest('button');
        if (!btn || btn.dataset.notifyState !== 'notified') return;
        if (!btn.dataset.hoverHtml) {
            btn.dataset.hoverHtml = btn.innerHTML;
        }
        btn.innerHTML = '<i class="fas fa-exclamation-triangle mr-2"></i>Cancel Notification';
        btn.style.backgroundColor = '#ef4444';
        btn.style.borderColor = '#ef4444';
        btn.style.color = 'white';
    });

    document.getElementById('auction-grid')?.addEventListener('mouseout', (e) => {
        const btn = e.target.closest('button');
        if (!btn || btn.dataset.notifyState !== 'notified') return;
        if (btn.dataset.hoverHtml) {
            btn.innerHTML = btn.dataset.hoverHtml;
            btn.style.backgroundColor = '';
            btn.style.borderColor = '';
            btn.style.color = '';
        }
    });

    // ----------------------------------------------------------
    // 10. Clicking the card image or title also navigates
    // ----------------------------------------------------------
    document.getElementById('auction-grid')?.addEventListener('click', (e) => {
        // Only navigate if clicking the image or title, not a button
        const card = e.target.closest('.auction-card');
        if (!card) return;
        if (e.target.closest('button')) return; // handled above

        const isImage = e.target.tagName === 'IMG';
        const isTitle = e.target.closest('h3');

        if (isImage || isTitle) {
            const auctionId = card.getAttribute('data-auction-id');
            if (auctionId) {
                window.location.href = `/ProductDetailsAuction?id=${encodeURIComponent(auctionId)}`;
            }
        }
    });

    // ----------------------------------------------------------
    // 11. Load More button
    // ----------------------------------------------------------
    document.querySelector('.mt-8 button')?.addEventListener('click', () => {
        showToast('No more auctions to load right now.', 'info');
    });

    // Ensure the page starts in the full "all auctions" state.
    const defaultTab = document.querySelector('.tab-pill[data-tab="all"]');
    if (defaultTab) {
        switchTab(defaultTab, 'all');
    } else {
        updateTabButtons('all', null);
        updateAuctionHeader('all');
    }

    document.querySelectorAll('.auction-card').forEach(card => {
        card.style.display = '';
    });

    // Initial render — apply default filters
    applyFilters();

    document.querySelectorAll('.auction-card').forEach(card => {
        syncWonAuctionToCart(card).catch(() => {});
    });
});

// Badge updates are handled globally by authguard.js

// ============================================================
// Navigate to Product Detail Page
// Reads card data and passes it as URL query params
// ============================================================

function navigateToProductPage(placeBidBtn) {
    if (!placeBidBtn) return;

    const card = placeBidBtn.closest('.auction-card');
    if (!card) return;

    const auctionId = card.getAttribute('data-auction-id');
    if (auctionId) {
        window.location.href = `/ProductDetailsAuction?id=${encodeURIComponent(auctionId)}`;
        return;
    }

    // Fallback: legacy query-param navigation
    const productId   = card.getAttribute('data-product-id') ?? '';
    const name        = card.querySelector('h3')?.textContent.trim() ?? '';
    const artist      = card.querySelector('p.text-sm')?.textContent.trim() ?? '';
    const currentBid  = card.querySelector('.text-xl.font-bold')?.textContent.trim() ?? '0.000 BD';
    const imgSrc      = card.querySelector('img')?.src ?? '';
    const category    = card.getAttribute('data-category') ?? '';
    const bidsCount   = card.querySelector('.text-xs.text-\\[\\#8b7355\\]')?.textContent.trim() ?? '0 bids';

    // Read countdown time remaining
    const countdownEl = card.querySelector('.countdown');
    const hoursLeft   = countdownEl?.dataset.hours   ?? '0';
    const minsLeft    = countdownEl?.dataset.minutes ?? '0';
    const secsLeft    = countdownEl?.dataset.seconds ?? '0';

    // Build query string so ProductDetailsAuction.html can read the data
    const params = new URLSearchParams({
        id:       productId,
        name:     name,
        artist:   artist,
        bid:      currentBid,
        img:      imgSrc,
        category: category,
        bids:     bidsCount,
        hours:    hoursLeft,
        minutes:  minsLeft,
        seconds:  secsLeft,
    });

    window.location.href = `/ProductDetailsAuction?${params.toString()}`;
}

// ============================================================
// Countdown logic
// ============================================================

function startCountdowns() {
    // Already initialised from data attributes in HTML — nothing extra needed.
}

function tickCountdowns() {
    document.querySelectorAll('.countdown').forEach(el => {
        const card = el.closest('.auction-card');
        if (card && card.classList.contains('refreshing')) return;

        let hours   = parseInt(el.dataset.hours,   10);
        let minutes = parseInt(el.dataset.minutes, 10);
        let seconds = parseInt(el.dataset.seconds, 10);

        if (hours <= 0 && minutes <= 0 && seconds <= 0) {
            if (card) refreshAuctionCard(card);
            return;
        }

        if (seconds > 0) {
            seconds--;
        } else {
            seconds = 59;
            if (minutes > 0) {
                minutes--;
            } else {
                minutes = 59;
                if (hours > 0) hours--;
            }
        }

        el.dataset.hours   = hours;
        el.dataset.minutes = minutes;
        el.dataset.seconds = seconds;
        el.textContent     = `${hours}h ${minutes}m ${seconds}s`;

        // Warning colour when under 5 minutes
        const parent = el.closest('span');
        if (parent && hours === 0 && minutes < 5) {
            parent.classList.add('bg-red-100', 'text-red-600');
            parent.classList.remove('bg-[#f5ebe0]', 'text-[#c17c5f]');
        }

    });
}

function markAuctionEnded(countdownEl) {
    const card = countdownEl.closest('.auction-card');
    if (!card || card.classList.contains('ended')) return;

    card.classList.add('ended');
    card.setAttribute('data-auction-status', 'ENDED');
    const badge = card.querySelector('.live-badge');
    if (badge) {
        badge.className = 'absolute top-3 left-3 bg-gray-500 text-white text-xs font-bold px-3 py-1 rounded-full';
        badge.textContent = 'ENDED';
    }
    const btn = card.querySelector('button');
    if (btn) {
        btn.textContent = 'Auction Ended';
        btn.className   = 'w-full py-2.5 bg-gray-300 text-gray-500 rounded-lg font-semibold cursor-not-allowed';
        btn.disabled    = true;
    }
    syncWonAuctionToCart(card).catch(() => {});
}

function refreshAuctionCard(card) {
    const auctionId = card.getAttribute('data-auction-id');
    if (!auctionId) return;

    card.classList.add('refreshing');
    
    fetch(window.location.pathname + '?_t=' + new Date().getTime())
        .then(res => res.text())
        .then(html => {
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            const newCard = doc.querySelector(`.auction-card[data-auction-id="${auctionId}"]`);
            if (newCard) {
                card.innerHTML = newCard.innerHTML;
                card.setAttribute('data-auction-status', newCard.getAttribute('data-auction-status'));
                card.className = newCard.className;
            } else {
                // If it wasn't found in the response, fallback to manual marking
                markAuctionEnded(card.querySelector('.countdown') || card);
            }
        })
        .catch(err => {
            console.error("Failed to refresh card", err);
            // Fallback
            markAuctionEnded(card.querySelector('.countdown') || card);
        })
        .finally(() => {
            card.classList.remove('refreshing');
            if (typeof applyFilters === 'function') applyFilters();
            syncWonAuctionToCart(card);
        });
}

// ============================================================
// Filter logic
// ============================================================

function applyFilters() {
    const grid         = document.getElementById('auction-grid');
    if (!grid) return;

    const cards        = grid.querySelectorAll('.auction-card');
    const priceBounds  = window.auctionPriceRange ? window.auctionPriceRange.get() : { min: 0, max: Infinity };
    const searchTerm   = (document.querySelector('aside input[type="text"]')?.value ?? '').toLowerCase().trim();
    const checkedBoxes = [...document.querySelectorAll('.custom-checkbox:checked')]
                            .map(cb => cb.closest('label')?.textContent.trim().toLowerCase() ?? '');

    let visibleCount = 0;

    cards.forEach(card => {
        // Search match
        const title  = card.querySelector('h3')?.textContent.toLowerCase() ?? '';
        const artist = card.querySelector('p.text-sm')?.textContent.toLowerCase() ?? '';
        const matchesSearch = !searchTerm || title.includes(searchTerm) || artist.includes(searchTerm);

        // Category match
        const cardCat   = card.getAttribute('data-category') ?? '';
        const matchesCat = _activeCategory === 'all' || cardCat === _activeCategory;

        // Price match
        const bidText  = card.querySelector('.auction-bid-amount')?.textContent ?? '0';
        const bidValue = parseFloat(bidText.replace(/[^0-9.]/g, '')) || 0;
        const matchesPrice = bidValue >= priceBounds.min && bidValue <= priceBounds.max;

        // Tab match
        let matchesTab = true;
        if      (_activeTab === 'upcoming') matchesTab = isUpcomingCard(card);
        else if (_activeTab === 'ended')    matchesTab = isEndedCard(card);
        else if (_activeTab === 'live')     matchesTab = isLiveCard(card);
        else if (_activeTab === 'all')      matchesTab = true;

        // Sidebar status checkbox match
        let matchesStatus = true;
        if (checkedBoxes.length > 0) {
            matchesStatus = checkedBoxes.some(s =>
                (s === 'live now'  && isLiveCard(card)) ||
                (s === 'upcoming'  && isUpcomingCard(card)) ||
                (s === 'ended'     && isEndedCard(card))
            );
        }

        const show = matchesSearch && matchesCat && matchesPrice && matchesTab && matchesStatus;
        card.style.display = show ? '' : 'none';
        if (show) visibleCount++;
    });

    // No-results message
    let noResults = document.getElementById('no-auction-results');
    const grid2   = document.getElementById('auction-grid');
    if (visibleCount === 0 && grid2) {
        if (!noResults) {
            noResults = document.createElement('div');
            noResults.id        = 'no-auction-results';
            noResults.className = 'col-span-3 text-center py-16 text-[#8b7355]';
            noResults.innerHTML = '<i class="fas fa-gavel text-4xl mb-4 block text-[#d4c5b5]"></i><p class="text-lg font-medium">No auctions match your filters.</p><p class="text-sm mt-1">Try a different tab, category, or price range.</p>';
            grid2.appendChild(noResults);
        }
        noResults.style.display = 'block';
    } else if (noResults) {
        noResults.style.display = 'none';
    }
}

function getCardStatus(card) {
    return (card.getAttribute('data-auction-status') || '').toUpperCase();
}
function isLiveCard(card) {
    return getCardStatus(card) === 'LIVE' && !card.classList.contains('ended');
}
function isUpcomingCard(card) {
    return getCardStatus(card) === 'UPCOMING';
}
function isEndedCard(card) {
    return getCardStatus(card) === 'ENDED' || card.classList.contains('ended');
}

function isCurrentUserWinner(card) {
    const winnerId = card.getAttribute('data-auction-winner-id') ?? '';
    const currentUserId = sessionStorage.getItem('userId') ?? '';
    return winnerId && currentUserId && winnerId === currentUserId;
}

async function syncWonAuctionToCart(card) {
    if (!card || card.dataset.auctionWinnerSynced === 'true') return;
    if (!isEndedCard(card) || !isCurrentUserWinner(card)) return;

    const productId = card.getAttribute('data-product-id');
    if (!productId || typeof window.addProductToCart !== 'function') return;

    card.dataset.auctionWinnerSynced = 'true';
    try {
        await window.addProductToCart(productId, 1);
        showToast('Congratulations! Your won auction item was added to your cart.', 'success');
    } catch (err) {
        console.warn('Auction winner cart sync failed', err);
    }
}

function sortAuctions(criteria) {
    const grid  = document.getElementById('auction-grid');
    if (!grid) return;
    const cards = [...grid.querySelectorAll('.auction-card')];

    cards.sort((a, b) => {
        if (criteria === 'ending-soonest') {
            return totalSeconds(a) - totalSeconds(b);
        }
        if (criteria === 'lowest-bid') {
            return getBidValue(a) - getBidValue(b);
        }
        if (criteria === 'highest-bid') {
            return getBidValue(b) - getBidValue(a);
        }
        return 0;
    });

    cards.forEach(c => grid.appendChild(c));
}

function totalSeconds(card) {
    const el = card.querySelector('.countdown');
    if (!el) return Infinity;
    return parseInt(el.dataset.hours,10)*3600 + parseInt(el.dataset.minutes,10)*60 + parseInt(el.dataset.seconds,10);
}

function getBidValue(card) {
    const text = card.querySelector('.auction-bid-amount')?.textContent ?? '0';
    return parseFloat(text.replace(/[^0-9.]/g, '')) || 0;
}

// ============================================================
// Toast helper
// ============================================================

function showToast(message, type = 'info') {
    document.getElementById('av-toast')?.remove();
    const colours = { success: 'bg-green-600', error: 'bg-red-500', info: 'bg-[#c17c5f]' };
    const toast = document.createElement('div');
    toast.id        = 'av-toast';
    toast.className = `fixed bottom-6 right-6 z-50 px-6 py-3 rounded-xl text-white font-medium shadow-lg ${colours[type]}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, 3500);
}
