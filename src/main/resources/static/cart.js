document.addEventListener('DOMContentLoaded', () => {

    const SHIPPING_COST = 2.00;
    let isShipping = true;

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
        } else {
            loginButtonWrapper?.classList.remove('hidden');
            userSection?.classList.add('hidden');
        }

        updateCartBadge();
    }

    window.selectDelivery = function(mode) {
        if (window.requireLoginForAction && !window.requireLoginForAction('Login/Register first to change delivery options.')) return;
        isShipping = (mode === 'ship');

        const shipCard = document.getElementById('card-ship');
        const pickupCard = document.getElementById('card-pickup');
        const shippingSection = document.getElementById('shipping-form-section');
        const pickupInfo = document.getElementById('pickup-info');
        const shippingRow = document.getElementById('shipping-cost-row');

        const radioShip   = document.getElementById('radio-ship');
        const radioPickup = document.getElementById('radio-pickup');

        const activeStyle = 'width:20px;height:20px;border-radius:50%;flex-shrink:0;margin-top:2px;border:2px solid #c17c5f;background-color:#c17c5f;display:flex;align-items:center;justify-content:center;';
        const inactiveStyle = 'width:20px;height:20px;border-radius:50%;flex-shrink:0;margin-top:2px;border:2px solid #e5e0d8;background-color:transparent;display:block;';
        const checkIcon = '<i class="fas fa-check" style="color:white;font-size:9px;"></i>';

        if (isShipping) {
            shipCard.classList.add('selected');
            pickupCard.classList.remove('selected');
            if (radioShip)   { radioShip.setAttribute('style', activeStyle);   radioShip.innerHTML   = checkIcon; }
            if (radioPickup) { radioPickup.setAttribute('style', inactiveStyle); radioPickup.innerHTML = ''; }
            shippingSection.style.display = 'block';
            pickupInfo.classList.add('hidden');
            if(shippingRow) {
                document.getElementById('shipping-label').textContent = 'Shipping';
                document.getElementById('shipping-cost-display').textContent = SHIPPING_COST.toFixed(2) + ' BD';
            }
        } else {
            pickupCard.classList.add('selected');
            shipCard.classList.remove('selected');
            if (radioPickup) { radioPickup.setAttribute('style', activeStyle);   radioPickup.innerHTML = checkIcon; }
            if (radioShip)   { radioShip.setAttribute('style', inactiveStyle);   radioShip.innerHTML   = ''; }
            shippingSection.style.display = 'none';
            pickupInfo.classList.remove('hidden');
            if(shippingRow) {
                document.getElementById('shipping-label').textContent = 'Pickup';
                document.getElementById('shipping-cost-display').textContent = 'Free';
            }
        }

        updateCartTotal();
    };

    window.updateQuantity = async function(button, change) {
        if (window.requireLoginForAction && !window.requireLoginForAction('Login/Register first to update item quantities.')) return;
        const cartItem = button.closest('.cart-item');
        const quantitySpan = cartItem.querySelector('.quantity-value');
        const unitPriceEl = cartItem.querySelector('.cart-unit-price');
        const subtotalEl = cartItem.querySelector('.item-subtotal');
        const orderItemId = cartItem.dataset.orderItemId;
        const maxQty = parseInt(cartItem.dataset.maxQuantity || '9999', 10);
        const unit = parseFloat(unitPriceEl.textContent.replace(' BD', ''));

        let qty = parseInt(quantitySpan.textContent, 10) + change;
        if (qty < 1) qty = 1;
        if (qty > maxQty) qty = maxQty;

        const email = sessionStorage.getItem('userEmail');
        if (!email || !orderItemId) return;

        try {
            const res = await fetch(`/api/cart/items/${orderItemId}`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                    'X-User-Email': email
                },
                body: JSON.stringify({ quantity: qty })
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data.message || 'Could not update quantity');
                return;
            }
            if (typeof data.itemCount === 'number') {
                sessionStorage.setItem('cartCount', String(data.itemCount));
                updateCartBadge();
            }
            quantitySpan.textContent = String(qty);
            subtotalEl.textContent = (unit * qty).toFixed(2) + ' BD';
            updateCartTotal();
        } catch (err) {
            console.error(err);
            alert('Could not update quantity');
        }
    };

    window.removeItem = async function(button) {
        if (window.requireLoginForAction && !window.requireLoginForAction('Login/Register first to remove items from your cart.')) return;
        const cartItem = button.closest('.cart-item');
        const orderItemId = cartItem.dataset.orderItemId;
        const email = sessionStorage.getItem('userEmail');
        if (!email || !orderItemId) return;

        cartItem.style.opacity = '0';
        cartItem.style.transform = 'translateX(-20px)';

        try {
            const res = await fetch(`/api/cart/items/${orderItemId}`, {
                method: 'DELETE',
                headers: { 'X-User-Email': email }
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data.message || 'Could not remove item');
                cartItem.style.opacity = '1';
                cartItem.style.transform = '';
                return;
            }
            if (typeof data.itemCount === 'number') {
                sessionStorage.setItem('cartCount', String(data.itemCount));
                updateCartBadge();
            }
            setTimeout(() => {
                cartItem.remove();
                updateCartTotal();
                checkCartEmpty();
            }, 200);
        } catch (err) {
            console.error(err);
            cartItem.style.opacity = '1';
            cartItem.style.transform = '';
        }
    };

    function checkCartEmpty() {
        const container = document.getElementById('cart-items-container');
        const emptyMsg = document.getElementById('empty-cart-msg');
        const summary = document.querySelector('.mt-4.pt-4.border-t-2');
        const confirmBtn = document.getElementById('confirm-btn');

        if (container && !container.querySelectorAll('.cart-item').length) {
            emptyMsg?.classList.remove('hidden');
            if (summary) summary.style.display = 'none';
            if (confirmBtn) {
                confirmBtn.disabled = true;
                confirmBtn.classList.add('opacity-50', 'cursor-not-allowed');
            }
        } else {
            emptyMsg?.classList.add('hidden');
            if (summary) summary.style.display = 'block';
            if (confirmBtn) {
                confirmBtn.disabled = false;
                confirmBtn.classList.remove('opacity-50', 'cursor-not-allowed');
            }
        }
    }

    window.selectPayment = function(card) {
        if (window.requireLoginForAction && !window.requireLoginForAction('Login/Register first to change payment method.')) return;
        document.querySelectorAll('.payment-card').forEach(c => {
            c.classList.remove('selected');
            const radio = c.querySelector('input[type="radio"]');
            const indicator = c.querySelector('.w-6.h-6');
            if (indicator) {
                indicator.className = 'w-6 h-6 border-2 border-[#e5e0d8] rounded-full mr-4';
                indicator.innerHTML = '';
            }
            if (radio) radio.checked = false;
        });

        card.classList.add('selected');
        const radio = card.querySelector('input[type="radio"]');
        const indicator = card.querySelector('.w-6.h-6');
        if (indicator) {
            indicator.className = 'w-6 h-6 border-2 border-[#c17c5f] bg-[#c17c5f] rounded-full mr-4 flex items-center justify-center';
            indicator.innerHTML = '<i class="fas fa-check text-white text-xs"></i>';
        }
        if (radio) radio.checked = true;
    };

    function updateCartTotal() {
        let itemsTotal = 0;
        document.querySelectorAll('.item-subtotal').forEach(el => {
            itemsTotal += parseFloat(el.textContent.replace(' BD', ''));
        });

        const shipping = isShipping ? SHIPPING_COST : 0;
        const grandTotal = itemsTotal + shipping;

        const itemsSubtotalEl = document.getElementById('items-subtotal');
        const cartTotalEl = document.getElementById('cart-total');
        const confirmBtn = document.getElementById('confirm-btn');

        if (itemsSubtotalEl) itemsSubtotalEl.textContent = itemsTotal.toFixed(2) + ' BD';
        if (cartTotalEl) cartTotalEl.textContent = grandTotal.toFixed(2) + ' BD';

        if (confirmBtn) {
            confirmBtn.innerHTML = `<i class="fas fa-check-circle mr-3"></i>Confirm Order (${grandTotal.toFixed(2)} BD)`;
        }
    }

    const confirmBtn = document.getElementById('confirm-btn');
    if (confirmBtn) {
        confirmBtn.addEventListener('click', () => {
            const selectedPayment = document.querySelector('.payment-card.selected input[type="radio"]');

            if (!selectedPayment) {
                alert('Please select a payment method.');
                return;
            }

            if (isShipping) {
                const selectedAddress = document.querySelector('input[name="selected_address"]:checked');
                if (!selectedAddress) {
                    alert('Please select a shipping address.');
                    return;
                }
            }

            const email = sessionStorage.getItem('userEmail');
            if (!email) {
                alert('Please log in to complete your order.');
                confirmBtn.disabled = false;
                updateCartTotal();
                return;
            }

            const paymentCard = document.querySelector('.payment-card.selected');
            const paymentMethod = paymentCard?.querySelector('.font-semibold')?.textContent?.trim() || 'Credit Card';
            const deliveryMode = isShipping ? 'ship' : 'pickup';
            let addressId = null;
            if (isShipping) {
                const selectedAddress = document.querySelector('input[name="selected_address"]:checked');
                addressId = selectedAddress ? parseInt(selectedAddress.value, 10) : null;
            }

            confirmBtn.innerHTML = '<i class="fas fa-spinner fa-spin mr-3"></i>Processing...';
            confirmBtn.disabled = true;

            fetch('/api/cart/checkout', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-User-Email': email
                },
                body: JSON.stringify({ paymentMethod, deliveryMode, addressId })
            })
            .then(res => res.json().then(data => ({ res, data })))
            .then(({ res, data }) => {
                if (!res.ok || !data.success) {
                    throw new Error(data.message || 'Checkout failed');
                }
                sessionStorage.setItem('cartCount', '0');
                updateCartBadge();
                const profile = sessionStorage.getItem('userProfile');
                if (profile) {
                    try {
                        const user = JSON.parse(profile);
                        user.totalOrders = data.totalOrders;
                        user.totalSpent = data.totalSpent;
                        sessionStorage.setItem('userProfile', JSON.stringify(user));
                    } catch (e) { /* ignore */ }
                }
                window.location.href = `/OrderConformation?orderId=${data.orderId}`;
            })
            .catch(err => {
                console.error(err);
                alert(err.message || 'Could not complete your order. Please try again.');
                confirmBtn.disabled = false;
                updateCartTotal();
            });
        });
    }

    function renderCartLine(item) {
        const div = document.createElement('div');
        div.className = 'cart-item flex flex-wrap sm:flex-nowrap gap-4 items-center';
        div.dataset.orderItemId = String(item.orderItemId);
        div.dataset.maxQuantity = String(item.maxQuantity);
        const title = escapeHtml(item.title);
        const img = escapeHtml(item.imageUrl);
        const unit = Number(item.unitPrice).toFixed(2);
        const sub = Number(item.subtotal).toFixed(2);
        const q = Number(item.quantity);
        div.innerHTML = `
            <img src="${img}" alt="${title}" class="cart-item-img">
            <div class="flex-1 min-w-0">
                <h3 class="font-semibold text-[#5c4a3d] truncate">${title}</h3>
                <p class="text-sm text-[#8b7355] mt-1">Unit: <span class="cart-unit-price text-[#c17c5f] font-bold">${unit} BD</span></p>
                <div class="flex items-center gap-3 mt-3">
                    <button type="button" class="quantity-btn w-8 h-8 rounded-lg border border-[#e5e0d8] text-[#5c4a3d]" onclick="updateQuantity(this, -1)">−</button>
                    <span class="quantity-value font-semibold text-[#5c4a3d] w-6 text-center">${q}</span>
                    <button type="button" class="quantity-btn w-8 h-8 rounded-lg border border-[#e5e0d8] text-[#5c4a3d]" onclick="updateQuantity(this, 1)">+</button>
                </div>
            </div>
            <div class="flex flex-col items-end gap-2 ml-auto">
                <span class="item-subtotal text-lg font-bold text-[#5c4a3d]">${sub} BD</span>
                <button type="button" class="text-sm text-red-500 hover:text-red-700" onclick="removeItem(this)">
                    <i class="fas fa-trash-alt mr-1"></i>Remove
                </button>
            </div>`;
        return div;
    }

    async function loadCartFromApi() {
        const container = document.getElementById('cart-items-container');
        if (!container) return;

        const loggedIn = sessionStorage.getItem('isLoggedIn') === 'true' || sessionStorage.getItem('loggedIn') === 'true';
        const email = sessionStorage.getItem('userEmail');

        if (!loggedIn || !email) {
            container.innerHTML = '';
            sessionStorage.setItem('cartCount', '0');
            updateCartBadge();
            checkCartEmpty();
            updateCartTotal();
            return;
        }

        try {
            const res = await fetch('/api/cart', { headers: { 'X-User-Email': email } });
            const data = await res.json().catch(() => ({}));
            if (!res.ok || !data.success) {
                throw new Error(data.message || 'Failed to load cart');
            }
            sessionStorage.setItem('cartCount', String(data.itemCount != null ? data.itemCount : 0));
            updateCartBadge();
            container.innerHTML = '';
            (data.items || []).forEach(item => container.appendChild(renderCartLine(item)));
            checkCartEmpty();
            updateCartTotal();
        } catch (e) {
            console.error('loadCartFromApi', e);
            container.innerHTML = '';
            checkCartEmpty();
            updateCartTotal();
        }
    }

    updateLoginState();
    loadCartFromApi().then(() => {
        loadCartAddresses();
        checkCartEmpty();
        updateCartTotal();
    });
});

function loadCartAddresses() {
    const email = sessionStorage.getItem('userEmail') || '';
    const grid = document.getElementById('cart-addresses-grid');
    const noAddresses = document.getElementById('cart-no-addresses');
    if (!grid) return;

    if (!email) {
        if (noAddresses) noAddresses.classList.remove('hidden');
        return;
    }

    fetch(`/api/addresses?email=${encodeURIComponent(email)}`)
        .then(r => r.json())
        .then(addresses => {
            grid.querySelectorAll('label.cart-address-item').forEach(el => el.remove());

            if (!addresses.length) {
                if (noAddresses) noAddresses.classList.remove('hidden');
                return;
            }
            if (noAddresses) noAddresses.classList.add('hidden');

            let firstChecked = false;
            addresses.forEach(addr => {
                const isFirst = !firstChecked && (addr.default || true);
                if (!firstChecked) firstChecked = true;
                const checked = addr.default ? 'checked' : '';
                const defBadge = addr.default
                    ? '<span class="ml-2 px-2 py-0.5 text-[10px] bg-green-100 text-green-700 rounded-full uppercase font-bold">Default</span>'
                    : '';
                const html = `
                    <label class="relative cursor-pointer group cart-address-item">
                        <input type="radio" name="selected_address" value="${addr.id}" class="peer sr-only" ${checked}>
                        <div class="h-full p-4 rounded-xl border-2 border-[#f8f5f0] bg-[#fcfaf7] transition-all
                                    peer-checked:border-[#c17c5f] peer-checked:bg-white hover:border-[#e5e0d8]">
                            <div class="flex items-center mb-3">
                                <i class="fas fa-map-marker-alt text-[#c17c5f] mr-2"></i>
                                <span class="font-bold text-[#5c4a3d]">${escapeHtml(addr.label)}</span>${defBadge}
                            </div>
                            <div class="text-sm text-[#8a7a6d] leading-relaxed">
                                ${escapeHtml(addr.street)}<br>
                                ${escapeHtml(addr.city)}<br>
                                ${escapeHtml(addr.country)}
                            </div>
                        </div>
                    </label>`;
                noAddresses.insertAdjacentHTML('beforebegin', html);
            });
        })
        .catch(err => console.error('Failed to load addresses for cart', err));
}

function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, c =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function updateNotificationBadge() {
    const badge = document.getElementById('notification-badge');
    if (!badge) return;
    const count = parseInt(sessionStorage.getItem('notificationCount') ?? '0', 10);
    badge.textContent = count;
    badge.style.display = count > 0 ? 'flex' : 'none';
}

function updateCartBadge() {
    document.querySelectorAll('.fa-shopping-cart')
        .forEach(icon => {
            const badge = icon.parentElement?.querySelector('span');
            if (!badge) return;
            const count = parseInt(sessionStorage.getItem('cartCount') ?? '0', 10);
            badge.textContent = count;
            badge.style.display = count > 0 ? 'flex' : 'none';
        });
}
