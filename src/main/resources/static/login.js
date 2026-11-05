// ============================================================
// login.js — ArtsyVibe Login / Register Page
// ============================================================

document.addEventListener('DOMContentLoaded', () => {

    // ----------------------------------------------------------
    // 1. Tab switching (Login ↔ Register)
    // ----------------------------------------------------------
    window.switchTab = function (tab) {
        const loginForm    = document.getElementById('login-form');
        const registerForm = document.getElementById('register-form');
        const tabBtns      = document.querySelectorAll('.tab-btn');

        if (tab === 'login') {
            loginForm.classList.remove('hidden');
            registerForm.classList.add('hidden');
            tabBtns[0].classList.add('active');
            tabBtns[1].classList.remove('active');
        } else {
            loginForm.classList.add('hidden');
            registerForm.classList.remove('hidden');
            tabBtns[0].classList.remove('active');
            tabBtns[1].classList.add('active');
        }
    };

    // ----------------------------------------------------------
    // 2. Customer / Artisan type toggle
    // ----------------------------------------------------------
    window.switchType = function (type) {
        const customerForm = document.getElementById('customer-form');
        const artisanForm  = document.getElementById('artisan-form');
        const typePills    = document.querySelectorAll('.type-pill');

        if (type === 'customer') {
            customerForm.classList.remove('hidden');
            artisanForm.classList.add('hidden');
            typePills[0].classList.add('active');
            typePills[1].classList.remove('active');
        } else {
            customerForm.classList.add('hidden');
            artisanForm.classList.remove('hidden');
            typePills[0].classList.remove('active');
            typePills[1].classList.add('active');
        }
    };

    // ----------------------------------------------------------
    // 3. Custom "Remember me" checkbox visual
    // ----------------------------------------------------------
    initCustomCheckboxes();

    document.querySelectorAll('.password-toggle').forEach(button => {
        button.addEventListener('click', () => togglePasswordVisibility(button));
    });

    document.querySelectorAll('input[data-password-criteria-target]').forEach(input => {
        const targetId = input.dataset.passwordCriteriaTarget;
        input.addEventListener('input', () => updatePasswordCriteria(input, targetId));
        if (targetId) updatePasswordCriteria(input, targetId);
    });

    // Navigation from the login page is unrestricted —
    // protected pages redirect to login themselves via initAuthGuard.

    // ----------------------------------------------------------
    // 5. Login form submission
    // ----------------------------------------------------------
    window.handleLogin = async function (event) {
        event.preventDefault();
        const form     = event.target;
        const email    = form.querySelector('input[type="email"]')?.value.trim().toLowerCase();
        const password = form.querySelector('input[type="password"]')?.value;

        if (!email || !password) {
            showFormError(form, 'Please fill in all fields.');
            return;
        }

        if (!isValidEmail(email)) {
            showFormError(form, 'Please enter a valid email address.');
            return;
        }

        showButtonLoading(form.querySelector('button[type="submit"]'), 'Logging in…');

        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ email, password })
            });
            const data = await response.json();

            if (response.ok && data.success) {
                // Store minimal session data
                sessionStorage.setItem('loggedIn', 'true');
                sessionStorage.setItem('isLoggedIn', 'true');
                sessionStorage.setItem('userEmail', data.user.email);
                sessionStorage.setItem('userName', data.user.name);
                sessionStorage.setItem('userRole', data.user.role);
                sessionStorage.setItem('userId', data.user.userId);
                sessionStorage.setItem('userProfile', JSON.stringify(data.user));

                if (typeof window.syncCartCountFromServer === 'function') {
                    await window.syncCartCountFromServer();
                }
                if (typeof window.syncNotificationCountFromServer === 'function') {
                    await window.syncNotificationCountFromServer();
                }

                // Redirect back to the originally requested page (if any)
                const url = new URL(window.location.href);
                const next = url.searchParams.get('next') || sessionStorage.getItem('postLoginNext');
                sessionStorage.removeItem('postLoginNext');

                const userRole = (data.user.role || '').toUpperCase();
                if (!next && userRole === 'ARTISAN') {
                    window.location.href = `/artisanDashboard?id=${data.user.userId}`;
                } else if (!next && userRole === 'ADMIN') {
                    window.location.href = '/adminDashboard';
                } else {
                    window.location.href = next || '/';
                }
            } else {
                showFormError(form, data.message || 'Invalid email or password');
                resetButtonLoading(form.querySelector('button[type="submit"]'), 'Login');
            }
        } catch (error) {
            showFormError(form, 'An error occurred. Please try again.');
            resetButtonLoading(form.querySelector('button[type="submit"]'), 'Login');
        }
    };

    // ----------------------------------------------------------
    // 5. Customer registration form submission
    // ----------------------------------------------------------
    window.handleRegister = async function (event) {
        event.preventDefault();
        const form = event.target;
        const isArtisan = form.id === 'artisan-form';

        const fullName = form.querySelector('input[name="fullName"]')?.value.trim() || '';
        const email = form.querySelector('input[name="email"]')?.value.trim().toLowerCase() || '';
        const countryCode = form.querySelector('select[name="countryCode"]')?.value || '+880';
        const phoneRaw = form.querySelector('input[name="phone"]')?.value.trim() || '';
        const password = form.querySelector('input[name="password"]')?.value || '';
        const confirmPassword = form.querySelector('input[name="confirmPassword"]')?.value || '';
        const shopName = form.querySelector('input[name="shopName"]')?.value.trim() || '';
        const biography = form.querySelector('textarea[name="biography"]')?.value.trim() || '';

        if (!fullName || !email || !phoneRaw || !password || (isArtisan ? false : !confirmPassword)) {
            showFormError(form, 'Please fill in all required fields.');
            return;
        }

        if (!isValidEmail(email)) {
            showFormError(form, 'Please enter a valid email address.');
            return;
        }

        const phoneDigits = phoneRaw.replace(/\D/g, '');
        if (!isValidPhoneNumber(phoneDigits)) {
            showFormError(form, 'Please enter a valid phone number.');
            return;
        }
        const phone = `${countryCode}${phoneDigits}`;

        if (!isValidPasswordStrength(password)) {
            showFormError(form, 'Password must be at least 8 characters and include uppercase letters and numbers.');
            form.querySelector('input[name="password"]')?.focus();
            return;
        }

        if (!isArtisan && password !== confirmPassword) {
            showFormError(form, 'Passwords do not match.');
            form.querySelector('input[name="confirmPassword"]')?.focus();
            return;
        }

        const submitBtn = form.querySelector('button[type="submit"]');
        showButtonLoading(submitBtn, isArtisan ? 'Sending request…' : 'Creating account…');

        const payload = {
            name: fullName,
            email: email,
            phone: phone,
            password: password
        };
        let endpoint = '/api/auth/register/customer';

        if (isArtisan) {
            payload.shopName = shopName;
            payload.biography = biography;
            const profileInput = document.getElementById('artisan-profile-picture') ||
                form.querySelector('#artisan-profile-upload input[type="file"]') ||
                form.querySelectorAll('input[type="file"]')[0];

            if (profileInput?.files?.length) {
                try {
                    payload.profilePicture = await readFileAsDataUrl(profileInput.files[0]);
                } catch (err) {
                    showFormError(form, err.message || 'Could not read profile picture.');
                    resetButtonLoading(submitBtn, 'Send Request to Create Artisan Account');
                    return;
                }
            }
            endpoint = '/api/auth/register/artisan';
        }

        try {
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });
            const data = await response.json();

            if (response.ok && data.success) {
                if (isArtisan) {
                    showToast('Registration request sent to admin! You can log in once approved.', 'success');
                    setTimeout(() => {
                        window.location.href = '/login?message=' + encodeURIComponent('Your artisan registration request has been submitted and is pending admin approval.');
                    }, 2500);
                } else {
                    sessionStorage.setItem('loggedIn', 'true');
                    sessionStorage.setItem('isLoggedIn', 'true');
                    sessionStorage.setItem('userEmail', data.user.email);
                    sessionStorage.setItem('userName', data.user.name);
                    sessionStorage.setItem('userRole', data.user.role);
                    sessionStorage.setItem('userId', data.user.userId);
                    sessionStorage.setItem('userProfile', JSON.stringify(data.user));
                    sessionStorage.setItem('cartCount', '0');

                    showToast('Account created! Welcome to ArtsyVibe.', 'success');
                    setTimeout(() => {
                        const url = new URL(window.location.href);
                        const next = url.searchParams.get('next') || sessionStorage.getItem('postLoginNext');
                        sessionStorage.removeItem('postLoginNext');
                        window.location.href = next || '/';
                    }, 1500);
                }
            } else {
                showFormError(form, data.message || 'Registration failed');
                resetButtonLoading(submitBtn, isArtisan ? 'Send Request to Create Artisan Account' : 'Create Account');
            }
        } catch (error) {
            showFormError(form, 'An error occurred. Please try again.');
            resetButtonLoading(submitBtn, isArtisan ? 'Send Request to Create Artisan Account' : 'Create Account');
        }
    };

    // ----------------------------------------------------------
    // 6. File upload drag-and-drop areas
    // ----------------------------------------------------------
    document.querySelectorAll('.border-dashed').forEach(area => {
        area.addEventListener('click', () => {
            area.querySelector('input[type="file"]')?.click();
        });

        area.addEventListener('dragover', (e) => {
            e.preventDefault();
            area.classList.add('border-[#c17c5f]', 'bg-[#faf9f6]');
        });

        area.addEventListener('dragleave', () => {
            area.classList.remove('border-[#c17c5f]', 'bg-[#faf9f6]');
        });

        area.addEventListener('drop', (e) => {
            e.preventDefault();
            area.classList.remove('border-[#c17c5f]', 'bg-[#faf9f6]');
            const fileInput = area.querySelector('input[type="file"]');
            if (fileInput && e.dataTransfer.files.length) {
                fileInput.files = e.dataTransfer.files;
                updateFileLabel(area, e.dataTransfer.files[0].name);
            }
        });

        const fileInput = area.querySelector('input[type="file"]');
        fileInput?.addEventListener('change', () => {
            if (fileInput.files.length) {
                updateFileLabel(area, fileInput.files[0].name);
            }
        });
    });

    // ----------------------------------------------------------
    // 7. Pre-fill from URL params (e.g. ?tab=register)
    // ----------------------------------------------------------
    const params = new URLSearchParams(window.location.search);
    if (params.get('tab') === 'register') {
        switchTab('register');
    }
    const message = params.get('message');
    if (message === 'Suspended') {
        showToast('Your account has been suspended because you violated our Terms of Service and Policies.', 'error');
    } else if (message === 'Unauthorized') {
        showToast('You are not authorized to access this page.', 'error');
    } else if (message) {
        showToast(message, 'info');
    }
});

// ============================================================
// Helpers
// ============================================================

function initCustomCheckboxes() {
    document.querySelectorAll('.Remember-checkbox').forEach(checkbox => {
        checkbox.addEventListener('change', function () {
            toggleCheckboxVisual(this);
        });
    });
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

function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function isValidPasswordStrength(password) {
    return password.length >= 8
        && /[A-Z]/.test(password)
        && /[a-z]/.test(password)
        && /[0-9]/.test(password);
}

function isValidPhoneNumber(phone) {
    return /^[0-9]{7,15}$/.test(phone);
}

function updatePasswordCriteria(input, criteriaId) {
    const criteriaList = document.getElementById(criteriaId);
    if (!criteriaList) return;
    const password = input.value || '';
    const checks = [
        { regex: /.{8,}/, item: 'At least 8 characters' },
        { regex: /[A-Z]/, item: 'At least one uppercase letter' },
        { regex: /[a-z]/, item: 'At least one lowercase letter' },
        { regex: /[0-9]/, item: 'At least one number' }
    ];
    Array.from(criteriaList.children).forEach((node, index) => {
        const passed = checks[index] && checks[index].regex.test(password);
        node.classList.toggle('text-[#065f46]', passed);
        node.classList.toggle('text-[#8b7355]', !passed);
        const icon = node.querySelector('i');
        if (icon) {
            icon.classList.toggle('text-green-600', passed);
            icon.classList.toggle('text-[#8b7355]', !passed);
        }
    });
}

function togglePasswordVisibility(button) {
    const wrapper = button.closest('.password-field');
    const input = wrapper ? wrapper.querySelector('input[type="password"], input[type="text"]') : null;
    if (!input) return;
    const isHidden = input.type === 'password';
    input.type = isHidden ? 'text' : 'password';
    const icon = button.querySelector('i');
    if (icon) {
        icon.className = isHidden ? 'fas fa-eye-slash' : 'fas fa-eye';
    }
}

function showFormError(form, message) {
    // Remove existing error
    form.querySelector('.form-error')?.remove();

    const div = document.createElement('div');
    div.className = 'form-error text-red-500 text-sm mt-2 text-center';
    div.textContent = message;
    form.querySelector('button[type="submit"]')?.before(div);

    setTimeout(() => div.remove(), 4000);
}

function showButtonLoading(btn, text) {
    if (!btn) return;
    btn.dataset.originalText = btn.textContent;
    btn.textContent = text;
    btn.disabled = true;
    btn.classList.add('opacity-75', 'cursor-not-allowed');
}

function resetButtonLoading(btn, text) {
    if (!btn) return;
    btn.textContent = text ?? btn.dataset.originalText ?? 'Submit';
    btn.disabled = false;
    btn.classList.remove('opacity-75', 'cursor-not-allowed');
}

function updateFileLabel(area, filename) {
    const para = area.querySelector('p');
    if (para) para.textContent = `Selected: ${filename}`;
}

function readFileAsDataUrl(file) {
    const maxBytes = 2 * 1024 * 1024;
    if (file.size > maxBytes) {
        return Promise.reject(new Error('Profile picture must be 2MB or smaller.'));
    }
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = (e) => resolve(e.target.result);
        reader.onerror = () => reject(new Error('Could not read profile picture.'));
        reader.readAsDataURL(file);
    });
}

function showToast(message, type = 'info') {
    document.getElementById('av-toast')?.remove();
    const colours = { success: 'bg-green-600', error: 'bg-red-500', info: 'bg-[#c17c5f]' };
    const toast = document.createElement('div');
    toast.id = 'av-toast';
    toast.className = `fixed bottom-6 right-6 z-50 px-6 py-3 rounded-xl text-white font-medium shadow-lg ${colours[type]}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, 3000);
}