// ============================================================
// my-profile.js — ArtsyVibe My Profile Page
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
        } else {
            loginButtonWrapper?.classList.remove('hidden');
            userSection?.classList.add('hidden');
        }

        updateCartBadge();
    }

    // ----------------------------------------------------------
    // 1. Role — toggle artisan section
    // ----------------------------------------------------------
    const userProfileStr = sessionStorage.getItem('userProfile');
    const userEmail = sessionStorage.getItem('userEmail');
    if (userEmail) {
        fetch(`/api/user/me?email=${encodeURIComponent(userEmail)}`)
            .then(r => r.json())
            .then(data => {
                if (data.success) {
                    sessionStorage.setItem('userProfile', JSON.stringify(data.user));
                    sessionStorage.setItem('userName', data.user.name);
                    // Refresh the UI with latest data
                    renderProfileData(data.user);
                }
            })
            .catch(err => console.error("Failed to sync profile data", err));
    }

    function renderProfileData(userProfile) {
        const userRole = userProfile.role || 'CUSTOMER';
        
        // Update sidebar info
        const sidebarName = document.querySelector('aside h2');
        const sidebarEmail = document.querySelector('aside p');
        const sidebarRoleBadge = document.getElementById('role-badge');
        const profileImg = document.querySelector('aside img.w-40.h-40');
        
        if (sidebarName) sidebarName.textContent = userProfile.name || 'John Doe';
        if (sidebarEmail) sidebarEmail.textContent = userProfile.email || 'john@example.com';
        if (sidebarRoleBadge) sidebarRoleBadge.textContent = userRole === 'ARTISAN' ? 'Artisan' : 'Customer';
        if (profileImg) {
            profileImg.src = userProfile.profilePicture || 'https://cdn-icons-png.flaticon.com/512/149/149071.png';
        }
        
        // Update form inputs
        const nameInput = document.getElementById('fullName');
        const emailInput = document.getElementById('emailAddress');
        const phoneInput = document.getElementById('phoneNumber');
        if (nameInput) nameInput.value = userProfile.name || '';
        if (emailInput) emailInput.value = userProfile.email || '';
        if (phoneInput) phoneInput.value = userProfile.phone || '';
        
        // Update shop info if artisan
        if (userRole === 'ARTISAN') {
            const artisanSection = document.getElementById('artisan-section');
            if (artisanSection) {
                artisanSection.classList.remove('hidden');
                const shopNameInput = artisanSection.querySelector('input[type="text"]');
                const bioTextarea = artisanSection.querySelector('textarea');
                
                if (shopNameInput) shopNameInput.value = userProfile.shopName || '';
                if (bioTextarea) bioTextarea.value = userProfile.biography || '';
            }

            const customerTabs = document.getElementById('customer-account-tabs');
            if (customerTabs) {
                customerTabs.innerHTML = `
                    <div class="flex flex-wrap gap-2">
                        <a href="/artisanDashboard?id=${sessionStorage.getItem('userId') || ''}" class="px-4 py-2 bg-[#faf9f6] text-[#8b7355] hover:text-[#c17c5f] rounded-lg font-medium transition-colors">
                            <i class="fas fa-chart-line mr-2"></i>Artisan Dashboard
                        </a>
                        <a href="/profile" class="px-4 py-2 bg-[#c17c5f] text-white rounded-lg font-medium transition-colors">
                            <i class="fas fa-user mr-2"></i>My Profile
                        </a>
                    </div>`;
            }

            const cartLink = document.getElementById('cart-link');
            if (cartLink) cartLink.classList.add('hidden');

            const notificationsLink = document.getElementById('notifications-link');
            if (notificationsLink) notificationsLink.href = '/artisanNotification?id=' + (sessionStorage.getItem('userId') || '');
        }

        const statsSection = document.getElementById('profile-stats-section');
        const totalOrdersEl = document.getElementById('total-orders');
        const totalSpentEl = document.getElementById('total-spent');
        if (userRole === 'CUSTOMER') {
            statsSection?.classList.remove('hidden');
            const orders = userProfile.totalOrders ?? 0;
            const spent = userProfile.totalSpent ?? 0;
            if (totalOrdersEl) totalOrdersEl.textContent = orders > 0 ? String(orders) : '—';
            if (totalSpentEl) totalSpentEl.textContent = spent > 0 ? Number(spent).toFixed(3) + ' BD' : '—';
        } else {
            statsSection?.classList.add('hidden');
        }

        setRole(userRole.toLowerCase());
    }

    // Initial render from session storage if available
    if (userProfileStr) {
        try {
            renderProfileData(JSON.parse(userProfileStr));
        } catch (e) {}
    }

    // ----------------------------------------------------------
    // 2. Custom checkboxes (modals)
    // ----------------------------------------------------------
    document.querySelectorAll('input[type="checkbox"].sr-only, #edit-address-default')
        .forEach(cb => cb.addEventListener('change', () => toggleCheckboxVisual(cb)));

    // ----------------------------------------------------------
    // 3. Profile picture upload
    // ----------------------------------------------------------
    const cameraBtn = document.querySelector('label .fa-camera')?.closest('label');
    cameraBtn?.querySelector('input[type="file"]')?.addEventListener('change', function () {
        if (!this.files.length) return;
        const file = this.files[0];
        if (file.size > 2 * 1024 * 1024) {
            showToast('Profile picture must be 2MB or smaller.', 'error');
            this.value = '';
            return;
        }
        const reader = new FileReader();
        reader.onload = (e) => {
            const imageUrl = e.target.result;
            const img = document.querySelector('.w-40.h-40.rounded-full');
            if (img) img.src = imageUrl;

            // Save to database permanently
            const userEmail = sessionStorage.getItem('userEmail');
            if (userEmail) {
                fetch('/api/user/update-profile-picture', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: userEmail, imageUrl: imageUrl })
                })
                .then(r => r.json().catch(() => ({})))
                .then(data => {
                    if (!data.success) {
                        showToast(data.message || 'Failed to save profile picture.', 'error');
                        return;
                    }
                    {
                        showToast('Profile picture updated permanently!', 'success');
                        // Update session storage and UI
                        sessionStorage.setItem('userProfile', JSON.stringify(data.user));
                        renderProfileData(data.user);
                        if (window.updateNavAuthState) window.updateNavAuthState();
                    }
                })
                .catch(err => {
                    console.error('Failed to update profile picture', err);
                    showToast('Failed to save profile picture to server.', 'error');
                });
            }
        };
        reader.readAsDataURL(file);
    });

    // Remove photo: the server falls back to the default avatar when imageUrl is empty.
    document.getElementById('remove-profile-picture')?.addEventListener('click', () => {
        if (!confirm('Remove your profile photo?')) return;
        fetch('/api/user/update-profile-picture', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ imageUrl: '' })
        })
            .then(r => r.json().catch(() => ({})))
            .then(data => {
                if (!data.success) {
                    showToast(data.message || 'Could not remove the photo.', 'error');
                    return;
                }
                showToast('Profile photo removed.', 'success');
                sessionStorage.setItem('userProfile', JSON.stringify(data.user));
                renderProfileData(data.user);
                const img = document.querySelector('.w-40.h-40.rounded-full');
                if (img) img.src = data.user.profilePicture;
                if (window.updateNavAuthState) window.updateNavAuthState();
            })
            .catch(() => showToast('Could not remove the photo.', 'error'));
    });

    // ----------------------------------------------------------
    // 4. File upload areas in artisan section
    // ----------------------------------------------------------
    document.querySelectorAll('.border-dashed').forEach(area => {
        area.addEventListener('click', (e) => {
            if (e.target.closest('.fa-plus')) return;
            area.querySelector('input[type="file"]')?.click();
        });

        const fileInput = area.querySelector('input[type="file"]');
        fileInput?.addEventListener('change', () => {
            if (fileInput.files.length) {
                const p = area.querySelector('p');
                if (p) p.textContent = `Selected: ${fileInput.files[0].name}`;
            }
        });
    });

    // ----------------------------------------------------------
    // 5. Close modals when clicking backdrop
    // ----------------------------------------------------------
    ['add-address-modal', 'edit-address-modal'].forEach(id => {
        document.getElementById(id)?.addEventListener('click', (e) => {
            if (e.target === document.getElementById(id)) {
                id === 'add-address-modal' ? closeAddAddressModal() : closeEditAddressModal();
            }
        });
    });

    // ----------------------------------------------------------
    // 6. Keyboard: close modals on Escape
    // ----------------------------------------------------------
    document.addEventListener('keydown', (e) => {
        if (e.key !== 'Escape') return;
        closeAddAddressModal();
        closeEditAddressModal();
    });

    updateLoginState();
    loadAddresses();
});

// ============================================================
// Role management
// ============================================================

window.setRole = function (role) {
    const artisanSection = document.getElementById('artisan-section');
    const roleBadge      = document.getElementById('role-badge');
    if (role === 'artisan') {
        artisanSection?.classList.remove('hidden');
        if (roleBadge) roleBadge.textContent = 'Artisan';
    } else {
        artisanSection?.classList.add('hidden');
        if (roleBadge) roleBadge.textContent = 'Customer';
    }
};

// ============================================================
// Personal Information
// ============================================================

window.savePersonalInfo = function () {
    // Collect form data
    const nameInput = document.getElementById('fullName');
    const emailInput = document.getElementById('emailAddress');
    const phoneInput = document.getElementById('phoneNumber');
    const name = nameInput?.value.trim() || '';
    const email = emailInput?.value.trim() || sessionStorage.getItem('userEmail') || '';
    const phone = phoneInput?.value.trim() || '';

    if (!name || !email || !phone) {
        showToast('Please fill in all personal information fields before saving.', 'error');
        return;
    }

    // Prepare payload
    const payload = { name, email, phone };

    // Send request to backend
    console.log('Saving personal info payload:', payload);
    fetch('/api/user/update-profile', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
        .then(r => r.json().catch(() => ({ success: false })))
        .then(data => {
            if (data.success) {
                showToast('Personal information saved successfully!', 'success');
                if (data.user) {
                    sessionStorage.setItem('userProfile', JSON.stringify(data.user));
                    sessionStorage.setItem('userEmail', data.user.email || email);
                    sessionStorage.setItem('userName', data.user.name || name);
                    renderProfileData(data.user);
                }
            } else {
                showToast(data.message || 'Failed to save personal information.', 'error');
            }
        })

        .catch(err => {
            console.error('Save personal info error:', err);
            showToast('Could not reach the server. Please try again.', 'error');
        });
};

// ============================================================
// Password update
// ============================================================

window.updatePassword = function () {
    // Collect password fields
    const currentPwd = document.querySelector('section:nth-of-type(2) input[type="password"]:nth-of-type(1)').value;
    const newPwd = document.querySelector('section:nth-of-type(2) input[type="password"]:nth-of-type(2)').value;
    const confirmPwd = document.querySelector('section:nth-of-type(2) input[type="password"]:nth-of-type(3)').value;

    if (!currentPwd) {
        showToast('Please enter your current password.', 'error');
        return;
    }
    if (newPwd.length < 8) {
        showToast('New password must be at least 8 characters.', 'error');
        return;
    }
    if (newPwd !== confirmPwd) {
        showToast('New passwords do not match.', 'error');
        return;
    }

    const payload = {
        email: sessionStorage.getItem('userEmail'),
        oldPassword: currentPwd,
        newPassword: newPwd
    };

    fetch('/api/auth/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
        .then(r => r.json())
        .then(data => {
            if (data.success) {
                showToast('Password updated successfully!', 'success');
                // Clear fields
                document.querySelectorAll('section:nth-of-type(2) input[type="password"]').forEach(i => i.value = '');
            } else {
                showToast(data.message || 'Failed to update password.', 'error');
            }
        })
        .catch(() => showToast('Error updating password.', 'error'));

};

// ============================================================
// Shop Information
// ============================================================

window.saveShopInfo = function () {
    showToast('Shop information saved successfully!', 'success');
};

// ============================================================
// Delete Account
// ============================================================

/** Small modal with a masked password field. Resolves to the password, or null if cancelled. */
function askPassword(title) {
    return new Promise(resolve => {
        const overlay = document.createElement('div');
        overlay.className = 'fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4';
        overlay.innerHTML = `
            <form class="bg-white rounded-2xl p-6 w-full max-w-sm shadow-xl">
                <h3 class="text-lg font-bold text-[#5c4a3d] mb-2"></h3>
                <p class="text-sm text-[#8b7355] mb-4">Enter your password to confirm.</p>
                <input type="password" autocomplete="current-password" required
                       class="w-full px-4 py-2 border border-[#e5e0d8] rounded-lg mb-4 focus:outline-none focus:border-[#c17c5f]">
                <div class="flex justify-end gap-2">
                    <button type="button" data-cancel class="px-4 py-2 rounded-lg text-[#8b7355] hover:bg-[#faf9f6]">Cancel</button>
                    <button type="submit" class="px-4 py-2 rounded-lg bg-red-600 hover:bg-red-700 text-white font-medium">Delete account</button>
                </div>
            </form>`;
        overlay.querySelector('h3').textContent = title;
        const form = overlay.querySelector('form');
        const input = overlay.querySelector('input');
        const close = value => { overlay.remove(); resolve(value); };
        form.addEventListener('submit', e => { e.preventDefault(); close(input.value); });
        overlay.querySelector('[data-cancel]').addEventListener('click', () => close(null));
        document.body.appendChild(overlay);
        input.focus();
    });
}

window.deleteAccount = async function () {
    const message = 'Are you sure you want to delete your account? This action cannot be undone.';

    if (!confirm(message)) {
        showToast('Account deletion cancelled.', 'info');
        return;
    }
    const password = await askPassword('Delete your account');
    if (!password) {
        showToast('Account deletion cancelled.', 'info');
        return;
    }
    {
        fetch('/api/auth/delete-account', {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ password })
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                showToast('Account deleted successfully. You will be logged out shortly.', 'success');
                setTimeout(() => {
                    sessionStorage.removeItem('loggedIn');
                    sessionStorage.removeItem('isLoggedIn');
                    sessionStorage.removeItem('userEmail');
                    sessionStorage.removeItem('userName');
                    sessionStorage.removeItem('userProfile');
                    sessionStorage.removeItem('postLoginNext');
                    window.location.href = '/login';
                }, 2000);
            } else {
                showToast(data.message || 'Failed to delete account.', 'error');
            }
        })
        .catch(error => {
            console.error('Error deleting account:', error);
            showToast('An error occurred while deleting your account.', 'error');
        });
    }
};

window.logout = function () {
    const email = sessionStorage.getItem('userEmail');
    const doLogout = () => {
        sessionStorage.removeItem('loggedIn');
        sessionStorage.removeItem('isLoggedIn');
        sessionStorage.removeItem('userEmail');
        sessionStorage.removeItem('userName');
        sessionStorage.removeItem('cartCount');
        sessionStorage.removeItem('postLoginNext');
        showToast('You have been logged out.', 'success');
        setTimeout(() => window.location.href = '/login', 1000);
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

// ============================================================
// Address Management — DB-backed
// ============================================================

function getUserEmail() {
    return sessionStorage.getItem('userEmail') || '';
}

function addressCardHtml(addr) {
    const defBadge = addr.default
        ? '<span class="ml-2 px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded-full font-medium">Default</span>'
        : '';
    return `
        <div class="address-card bg-[#faf9f6] rounded-xl p-5 border border-[#e5e0d8] relative" data-id="${addr.id}">
            <div class="absolute top-4 right-4 flex gap-2">
                <button class="text-[#8b7355] hover:text-[#c17c5f] transition-colors text-sm font-medium" onclick="editAddress(this)">
                    <i class="fas fa-edit"></i>
                </button>
                <button class="text-[#8b7355] hover:text-red-500 transition-colors text-sm font-medium" onclick="deleteAddress(this)">
                    <i class="fas fa-trash"></i>
                </button>
            </div>
            <div class="flex items-start gap-3 mb-3">
                <i class="fas fa-map-marker-alt text-[#c17c5f] text-lg mt-0.5"></i>
                <div>
                    <span class="font-semibold text-[#5c4a3d]">${escapeHtml(addr.label)}</span>${defBadge}
                </div>
            </div>
            <p class="text-[#8b7355] text-sm leading-relaxed">${escapeHtml(addr.street)}<br>${escapeHtml(addr.city)}, ${escapeHtml(addr.zip)}<br>${escapeHtml(addr.country)}</p>
        </div>
    `;
}

function loadAddresses() {
    const email = getUserEmail();
    if (!email) return;
    fetch(`/api/addresses?email=${encodeURIComponent(email)}`)
        .then(r => r.json())
        .then(addresses => {
            const grid = document.getElementById('addresses-grid');
            if (!grid) return;
            const addCard = grid.querySelector('.fa-plus')?.closest('.address-card');
            grid.querySelectorAll('.address-card[data-id]').forEach(c => c.remove());
            addresses.forEach(addr => {
                addCard?.insertAdjacentHTML('beforebegin', addressCardHtml(addr));
            });
        })
        .catch(err => console.error('Failed to load addresses', err));
}

window.openAddAddressModal = function () {
    const modal = document.getElementById('add-address-modal');
    modal?.classList.remove('hidden');
    modal?.classList.add('flex');
};

window.closeAddAddressModal = function () {
    const modal = document.getElementById('add-address-modal');
    modal?.classList.add('hidden');
    modal?.classList.remove('flex');
    document.getElementById('add-address-form')?.reset();
    resetCheckboxVisuals(document.getElementById('add-address-modal'));
};

window.saveNewAddress = function (event) {
    event.preventDefault();
    const form    = event.target;
    const inputs  = form.querySelectorAll('input[type="text"]');
    const select  = form.querySelector('select');
    const cbInput = form.querySelector('input[type="checkbox"]');

    const label     = inputs[0]?.value.trim();
    const street    = inputs[1]?.value.trim();
    const city      = inputs[2]?.value.trim();
    const zip       = inputs[3]?.value.trim();
    const country   = select?.options[select.selectedIndex]?.text ?? '';
    const isDefault = cbInput?.checked ?? false;

    if (!label || !street || !city || !zip || !country || country.startsWith('Select')) {
        showToast('Please fill in all address fields.', 'error');
        return;
    }

    const payload = {
        userEmail: getUserEmail(),
        label, street, city, zip, country,
        default: isDefault
    };

    fetch('/api/addresses', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(r => r.json())
    .then(() => {
        closeAddAddressModal();
        loadAddresses();
        showToast('Address added successfully!', 'success');
    })
    .catch(() => showToast('Failed to save address.', 'error'));
};

window.editAddress = function (button) {
    const card      = button.closest('.address-card');
    const id        = card.dataset.id;
    const label     = card.querySelector('.font-semibold')?.textContent.trim() ?? '';
    const addrLines = card.querySelector('p')?.innerHTML.split('<br>') ?? [];
    const street    = addrLines[0]?.trim() ?? '';
    const cityZip   = addrLines[1]?.trim() ?? '';
    const country   = addrLines[2]?.trim() ?? '';
    const isDefault = card.querySelector('.bg-green-100') !== null;

    let city = cityZip, zip = '';
    if (cityZip.includes(',')) {
        [city, zip] = cityZip.split(',').map(s => s.trim());
    }

    document.getElementById('edit-address-label').value   = label;
    document.getElementById('edit-address-street').value  = street;
    document.getElementById('edit-address-city').value    = city;
    document.getElementById('edit-address-zip').value     = zip;
    document.getElementById('edit-address-country').value = getCountryCode(country);

    const cb     = document.getElementById('edit-address-default');
    const icon   = cb?.parentElement.querySelector('.checkbox-icon');
    const visual = cb?.parentElement.querySelector('div');
    if (cb) cb.checked = isDefault;
    if (isDefault) {
        if (icon) icon.style.opacity = '1';
        visual?.classList.add('bg-[#c17c5f]', 'border-[#c17c5f]');
    } else {
        if (icon) icon.style.opacity = '0';
        visual?.classList.remove('bg-[#c17c5f]', 'border-[#c17c5f]');
    }

    window.currentEditCard = card;
    window.currentEditId   = id;

    const modal = document.getElementById('edit-address-modal');
    modal?.classList.remove('hidden');
    modal?.classList.add('flex');
};

window.closeEditAddressModal = function () {
    const modal = document.getElementById('edit-address-modal');
    modal?.classList.add('hidden');
    modal?.classList.remove('flex');
    window.currentEditCard = null;
    window.currentEditId   = null;
};

window.saveEditedAddress = function (event) {
    event.preventDefault();
    const label     = document.getElementById('edit-address-label').value.trim();
    const street    = document.getElementById('edit-address-street').value.trim();
    const city      = document.getElementById('edit-address-city').value.trim();
    const zip       = document.getElementById('edit-address-zip').value.trim();
    const sel       = document.getElementById('edit-address-country');
    const country   = sel?.options[sel.selectedIndex]?.text ?? '';
    const isDefault = document.getElementById('edit-address-default')?.checked ?? false;

    if (!label || !street || !city || !zip || !country || country.startsWith('Select')) {
        showToast('Please fill in all address fields.', 'error');
        return;
    }

    const id = window.currentEditId;
    if (!id) return;

    const payload = {
        userEmail: getUserEmail(),
        label, street, city, zip, country,
        default: isDefault
    };

    fetch(`/api/addresses/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(r => r.json())
    .then(() => {
        closeEditAddressModal();
        loadAddresses();
        showToast('Address updated successfully!', 'success');
    })
    .catch(() => showToast('Failed to update address.', 'error'));
};

window.deleteAddress = function (button) {
    if (!confirm('Are you sure you want to delete this address?')) return;
    const card = button.closest('.address-card');
    const id   = card.dataset.id;
    fetch(`/api/addresses/${id}`, { method: 'DELETE' })
        .then(() => {
            card.remove();
            showToast('Address deleted.', 'info');
        })
        .catch(() => showToast('Failed to delete address.', 'error'));
};

// ============================================================
// Helpers
// ============================================================

function removeAllDefaultBadges() {
    document.querySelectorAll('#addresses-grid .bg-green-100').forEach(b => b.remove());
}

function toggleCheckboxVisual(checkbox) {
    const icon   = checkbox.parentElement.querySelector('.checkbox-icon');
    const visual = checkbox.parentElement.querySelector('div');
    if (checkbox.checked) {
        if (icon) icon.style.opacity = '1';
        visual?.classList.add('bg-[#c17c5f]', 'border-[#c17c5f]');
    } else {
        if (icon) icon.style.opacity = '0';
        visual?.classList.remove('bg-[#c17c5f]', 'border-[#c17c5f]');
    }
}

function resetCheckboxVisuals(container) {
    container?.querySelectorAll('input[type="checkbox"].sr-only').forEach(cb => {
        cb.checked = false;
        const icon   = cb.parentElement.querySelector('.checkbox-icon');
        const visual = cb.parentElement.querySelector('div');
        if (icon) icon.style.opacity = '0';
        visual?.classList.remove('bg-[#c17c5f]', 'border-[#c17c5f]');
    });
}

function getCountryCode(countryName) {
    const map = {
        'Saudi Arabia / المملكة العربية السعودية': 'SA',
        'United Arab Emirates / الإمارات العربية المتحدة': 'AE',
        'Egypt / مصر': 'EG',
        'Qatar / قطر': 'QA',
        'Kuwait / الكويت': 'KW',
        'Oman / سلطنة عمان': 'OM',
        'Bahrain / البحرين': 'BH',
        'Jordan / الأردن': 'JO',
        'Morocco / المغرب': 'MA',
        'Algeria / الجزائر': 'DZ',
        'Iraq / العراق': 'IQ',
        'Lebanon / لبنان': 'LB',
    };
    return map[countryName] ?? '';
}

function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, c =>
        ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

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
