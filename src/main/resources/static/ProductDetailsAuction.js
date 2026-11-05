document.addEventListener('DOMContentLoaded', () => {

    const loginButtonWrapper = document.getElementById('login-button-wrapper');
    const userSection = document.getElementById('user-section');
    const navUserName = document.getElementById('nav-user-name');
    const notificationBadge = document.getElementById('notification-badge');

    let auctionId = null;
    let displayStatus = 'LIVE';
    let hours = 0;
    let minutes = 0;
    let seconds = 0;
    let countdownInterval = null;

    const hoursEl   = document.getElementById('hours');
    const minutesEl = document.getElementById('minutes');
    const secondsEl = document.getElementById('seconds');
    const bidForm   = document.querySelector('form');
    const bidInput  = document.getElementById('bidAmount');
    const bidBtn    = document.getElementById('bid-submit-btn');

    function updateLoginState() {
        const loggedIn = sessionStorage.getItem('isLoggedIn') === 'true' || sessionStorage.getItem('loggedIn') === 'true';
        if (loggedIn) {
            loginButtonWrapper?.classList.add('hidden');
            userSection?.classList.remove('hidden');
            const userName = sessionStorage.getItem('userName') || 'John Doe';
            if (navUserName) navUserName.textContent = userName;
            updateNotificationBadge();
        } else {
            loginButtonWrapper?.classList.remove('hidden');
            userSection?.classList.add('hidden');
        }
        updateCartBadge();
    }

    function getCurrentBidValue() {
        const bidText = document.querySelector('#current-bid-display')?.textContent ?? '0';
        return parseInt(bidText.replace(/[^0-9]/g, ''), 10) || 0;
    }

    function updateMinBid(minBid) {
        if (!bidInput) return minBid;
        bidInput.min = minBid;
        bidInput.step = 1;
        bidInput.placeholder = `Min: ${minBid} BD`;
        return minBid;
    }

    function setCountdownFromSeconds(totalSeconds) {
        const safe = Math.max(0, totalSeconds);
        hours   = Math.floor(safe / 3600);
        minutes = Math.floor((safe % 3600) / 60);
        seconds = safe % 60;
        renderCountdown();
    }

    function renderCountdown() {
        if (hoursEl)   hoursEl.textContent   = String(hours).padStart(2, '0');
        if (minutesEl) minutesEl.textContent = String(minutes).padStart(2, '0');
        if (secondsEl) secondsEl.textContent = String(seconds).padStart(2, '0');
    }

    function startCountdown() {
        if (countdownInterval) clearInterval(countdownInterval);
        renderCountdown();
        countdownInterval = setInterval(() => {
            if (displayStatus !== 'LIVE') return;
            if (hours === 0 && minutes === 0 && seconds === 0) {
                clearInterval(countdownInterval);
                handleAuctionEnd();
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
            renderCountdown();
            const countdownBox = document.getElementById('countdown')?.parentElement;
            if (countdownBox && hours === 0 && minutes < 5) {
                countdownBox.style.borderColor = '#ef4444';
                countdownBox.style.backgroundColor = '#fff5f5';
            }
        }, 1000);
    }

    function applyAuctionData(data) {
        if (!data) return;
        auctionId = data.id;
        displayStatus = (data.displayStatus || 'LIVE').toUpperCase();

        document.title = `${data.name || 'Auction'} - Auction - Artisan Co-op`;

        const nameEl = document.querySelector('h1');
        if (nameEl && data.name) nameEl.textContent = data.name;

        const artistEl = document.querySelector('h1 + p, h1 ~ .text-earth, h1 ~ a.text-\\[\\#8b7355\\]');
        if (artistEl && data.artist) artistEl.textContent = `by ${data.artist}`;

        const categoryEl = document.querySelector('.inline-block.bg-\\[\\#f5ebe0\\]');
        if (categoryEl && data.category) categoryEl.textContent = data.category;

        const bidEl = document.getElementById('current-bid-display');
        if (bidEl) bidEl.textContent = `${data.currentHighestBid} BD`;

        const highestBidderEl = document.getElementById('highest-bidder-name');
        if (highestBidderEl) {
            highestBidderEl.textContent = data.highestBidderName || 'No bids yet';
        }

        const descEl = document.querySelector('.mb-6 p.text-\\[\\#8b7355\\]');
        if (descEl && data.description) descEl.textContent = data.description;

        if (data.imageUrl) {
            const mainImg = document.getElementById('mainImage');
            if (mainImg) {
                mainImg.src = data.imageUrl;
                mainImg.alt = data.name || '';
            }
            document.querySelectorAll('.thumbnail').forEach(t => { t.src = data.imageUrl; });
        }

        updateMinBid(data.minNextBid ?? (data.currentHighestBid + 1));

        if (displayStatus === 'LIVE') {
            setCountdownFromSeconds(data.secondsRemaining ?? 0);
            startCountdown();
            if (bidBtn) {
                bidBtn.disabled = false;
                bidBtn.innerHTML = '<i class="fas fa-gavel mr-2"></i>Place Bid';
            }
        } else if (displayStatus === 'UPCOMING') {
            if (bidBtn) {
                bidBtn.disabled = true;
                bidBtn.textContent = 'Auction Not Started';
            }
            showToast('This auction has not started yet.', 'info');
        } else {
            handleAuctionEnd();
        }
    }

    async function loadAuction() {
        const params = new URLSearchParams(window.location.search);
        const id = params.get('id');
        if (!id) {
            hydrateFromLegacyParams(params);
            startCountdown();
            updateMinBid(getCurrentBidValue() + 1);
            return;
        }

        try {
            const res = await fetch(`/api/auctions/${encodeURIComponent(id)}`);
            const payload = await res.json();
            if (!res.ok || !payload.auction) {
                showToast('Could not load auction details.', 'error');
                return;
            }
            applyAuctionData(payload.auction);
        } catch (err) {
            showToast('Could not load auction details.', 'error');
        }
    }

    function hydrateFromLegacyParams(params) {
        if (!params.has('name')) return;
        document.title = `${params.get('name')} - Auction - Artisan Co-op`;
        const nameEl = document.querySelector('h1');
        if (nameEl) nameEl.textContent = params.get('name');
        const bidValue = parseInt((params.get('bid') ?? '0').replace(/[^0-9]/g, ''), 10) || 0;
        const bidEl = document.getElementById('current-bid-display');
        if (bidEl) bidEl.textContent = `${bidValue} BD`;
        hours   = parseInt(params.get('hours')   ?? '0', 10);
        minutes = parseInt(params.get('minutes') ?? '0', 10);
        seconds = parseInt(params.get('seconds') ?? '0', 10);
        auctionId = params.get('id');
    }

    function handleAuctionEnd() {
        displayStatus = 'ENDED';
        if (bidBtn) {
            bidBtn.disabled = true;
            bidBtn.textContent = 'Auction Ended';
            bidBtn.className = bidBtn.className.replace('bg-[#c17c5f]', 'bg-gray-400').replace('hover:bg-[#a5664d]', '');
        }
        const countdownBox = document.getElementById('countdown')?.parentElement;
        if (countdownBox) {
            countdownBox.style.borderColor = '#9ca3af';
            countdownBox.style.backgroundColor = '#f3f4f6';
        }
    }

    function extendCountdownTwoMinutes() {
        let total = hours * 3600 + minutes * 60 + seconds;
        total += 120;
        setCountdownFromSeconds(total);
        showToast('Anti-snipe: auction extended by 2 minutes!', 'info');
    }

    bidForm?.addEventListener('submit', async function(e) {
        e.preventDefault();

        if (!(window.requireLoginForAction ? window.requireLoginForAction('Login/Register first to place a bid.') : true)) {
            return;
        }

        if (displayStatus === 'ENDED') {
            showToast('This auction has ended.', 'error');
            return;
        }
        if (displayStatus === 'UPCOMING') {
            showToast('This auction has not started yet.', 'error');
            return;
        }

        const rawBid = bidInput?.value ?? '';
        const bidAmount = parseInt(rawBid, 10);
        const minBid = updateMinBid(getCurrentBidValue() + 1);
        if (!rawBid || isNaN(bidAmount)) {
            showToast('Please enter a valid bid amount.', 'error');
            bidInput?.focus();
            return;
        }
        if (parseFloat(rawBid) !== bidAmount) {
            showToast('Bids must be whole BD amounts only (e.g. 211, 212 — no decimals).', 'error');
            bidInput?.focus();
            return;
        }
        if (bidAmount < minBid) {
            showToast(`Your bid must be at least ${minBid} BD (current bid + 1 BD minimum).`, 'error');
            bidInput?.focus();
            return;
        }
        // Bids are placed as the signed-in customer; the server takes the name from the account.
        if (typeof window.requireLoginForAction === 'function'
            && !window.requireLoginForAction('Please log in as a customer to place a bid.')) {
            return;
        }
        if (!auctionId) {
            showToast('Auction not found. Please return to the auctions page.', 'error');
            return;
        }

        if (bidBtn) {
            bidBtn.disabled = true;
            bidBtn.textContent = 'Placing bid...';
        }

        try {
            const res = await fetch(`/api/auctions/${encodeURIComponent(auctionId)}/bid`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: bidAmount }),
            });
            const result = await res.json().catch(() => ({}));

            if (!res.ok || !result.success) {
                showToast(result.message || 'Could not place bid.', 'error');
                return;
            }

            applyAuctionData(result.auction);
            if (result.extended) {
                extendCountdownTwoMinutes();
            }
            showToast(result.message || `Bid of ${bidAmount} BD placed successfully!`, 'success');
            bidForm.reset();
            updateMinBid(result.auction.minNextBid);
        } catch (err) {
            showToast('Could not place bid. Please try again.', 'error');
        } finally {
            if (bidBtn && displayStatus === 'LIVE') {
                bidBtn.disabled = false;
                bidBtn.innerHTML = '<i class="fas fa-gavel mr-2"></i>Place Bid';
            }
        }
    });

    document.getElementById('back-to-auctions')?.addEventListener('click', () => {
        window.location.href = '/auctions';
    });

    window.changeImage = function(thumbnail) {
        const mainImage = document.getElementById('mainImage');
        if (!mainImage) return;
        mainImage.style.opacity = '0.5';
        setTimeout(() => {
            const highResUrl = thumbnail.src.includes('unsplash.com')
                ? thumbnail.src.replace(/w=\d+&h=\d+/, 'w=600&h=500')
                : thumbnail.src;
            mainImage.src = highResUrl;
            mainImage.style.opacity = '1';
        }, 150);
        document.querySelectorAll('.thumbnail').forEach(t => t.classList.remove('active'));
        thumbnail.classList.add('active');
    };

    function showToast(message, type = 'info') {
        if (!document.getElementById('toast-styles')) {
            const style = document.createElement('style');
            style.id = 'toast-styles';
            style.innerHTML = `
                .toast-container { position: fixed; bottom: 20px; right: 20px; z-index: 9999; display: flex; flex-direction: column; gap: 10px; }
                .toast { padding: 12px 24px; border-radius: 8px; color: white; font-family: 'Inter', sans-serif; box-shadow: 0 4px 12px rgba(0,0,0,0.15); animation: slideIn 0.3s ease-out; display: flex; align-items: center; gap: 10px; min-width: 250px; }
                .toast.success { background-color: #10b981; border-left: 5px solid #059669; }
                .toast.error   { background-color: #dc2626; border-left: 5px solid #991b1b; }
                .toast.info    { background-color: #c17c5f; border-left: 5px solid #a5664d; }
                @keyframes slideIn { from { transform: translateX(100%); opacity: 0; } to { transform: translateX(0); opacity: 1; } }
            `;
            document.head.appendChild(style);
            if (!document.querySelector('.toast-container')) {
                document.body.insertAdjacentHTML('beforeend', '<div class="toast-container"></div>');
            }
        }
        const container = document.querySelector('.toast-container');
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.innerHTML = `<i class="fas fa-${type === 'success' ? 'check-circle' : type === 'error' ? 'times-circle' : 'info-circle'}"></i> <span>${escapeHtml(message)}</span>`;
        container.appendChild(toast);
        setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, 3500);
    }

    loadAuction();
    updateLoginState();
});

function updateNotificationBadge() {
    const badge = document.getElementById('notification-badge');
    if (!badge) return;
    const count = parseInt(sessionStorage.getItem('notificationCount') ?? '0', 10);
    badge.textContent = count;
    badge.style.display = count > 0 ? 'flex' : 'none';
}

function updateCartBadge() {
    document.querySelectorAll('.fa-shopping-cart').forEach(icon => {
        const badge = icon.parentElement?.querySelector('span');
        if (!badge) return;
        const count = parseInt(sessionStorage.getItem('cartCount') ?? '0', 10);
        badge.textContent = count;
    });
}
