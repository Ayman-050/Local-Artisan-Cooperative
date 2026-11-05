// ============================================================
//
// forget-password.js: ArtsyVibe Forgot Password Page
// ============================================================

document.addEventListener('DOMContentLoaded', () => {

    // ----------------------------------------------------------
    // 1. Recovery form submission
    // ----------------------------------------------------------
    const form        = document.querySelector('form');
    const emailInput  = form?.querySelector('input[type="email"]');
    const submitBtn   = form?.querySelector('button[type="submit"]');

    window.handleRecoveryEmail = function (event) {
        event.preventDefault();

        const email = emailInput?.value.trim();

        // Clear previous errors
        clearFormErrors(form);

        // Validation
        if (!email) {
            showFieldError(emailInput, 'Please enter your email address.');
            return;
        }

        if (!isValidEmail(email)) {
            showFieldError(emailInput, 'Please enter a valid email address.');
            return;
        }

        // Show loading state
        showButtonLoading(submitBtn, 'Sending…');

        // Simulate API call: replace with real backend request
        setTimeout(() => {
            resetButtonLoading(submitBtn, 'Send Recovery Email');
            showSuccessState(email);
        }, 1500);
    };

    // ----------------------------------------------------------
    // 2. Email input: clear error on change
    // ----------------------------------------------------------
    emailInput?.addEventListener('input', () => clearFormErrors(form));

    // ----------------------------------------------------------
    // 3. Back to login link: preserve any email pre-fill from URL
    // ----------------------------------------------------------
    const params = new URLSearchParams(window.location.search);
    const prefill = params.get('email');
    if (prefill && emailInput) {
        emailInput.value = prefill;
    }
});

// ============================================================
// Helpers
// ============================================================

function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function showFieldError(input, message) {
    if (!input) return;
    input.classList.add('border-red-400', 'focus:border-red-400');
    input.classList.remove('focus:border-[#c17c5f]');

    const existing = input.parentElement.querySelector('.field-error');
    if (existing) return;

    const err = document.createElement('p');
    err.className = 'field-error text-red-500 text-sm mt-1';
    err.textContent = message;
    input.after(err);
}

function clearFormErrors(form) {
    if (!form) return;
    form.querySelectorAll('.field-error').forEach(el => el.remove());
    form.querySelectorAll('input').forEach(input => {
        input.classList.remove('border-red-400');
        input.classList.add('focus:border-[#c17c5f]');
    });
}

function showButtonLoading(btn, text) {
    if (!btn) return;
    btn.dataset.originalText = btn.textContent;
    btn.textContent          = text;
    btn.disabled             = true;
    btn.classList.add('opacity-75', 'cursor-not-allowed');
}

function resetButtonLoading(btn, text) {
    if (!btn) return;
    btn.textContent = text ?? btn.dataset.originalText ?? 'Submit';
    btn.disabled    = false;
    btn.classList.remove('opacity-75', 'cursor-not-allowed');
}

/**
 * Replace the form with a success message after sending the recovery email.
 * @param {string} email
 */
function showSuccessState(email) {
    const card = document.querySelector('.bg-white.rounded-2xl');
    if (!card) return;

    card.innerHTML = `
        <div class="text-center py-4">
            <div class="inline-flex items-center justify-center w-20 h-20 bg-green-100 rounded-full mb-6">
                <i class="fas fa-check text-4xl text-green-600"></i>
            </div>
            <h2 class="text-2xl font-bold text-[#5c4a3d] mb-3" style="font-family:'Playfair Display',serif;">
                Check Your Email
            </h2>
            <p class="text-[#8b7355] mb-2 text-lg">
                We've sent a password recovery link to:
            </p>
            <p class="font-semibold text-[#5c4a3d] mb-8">${escapeHtml(email)}</p>
            <p class="text-sm text-[#8b7355] mb-6">
                Didn't receive it? Check your spam folder or
                <button onclick="location.reload()" class="text-[#c17c5f] hover:text-[#a5664d] font-medium underline">
                    try again
                </button>.
            </p>
            <a href="/login"
               class="inline-flex items-center text-[#c17c5f] hover:text-[#a5664d] font-medium text-lg transition-colors">
                <i class="fas fa-arrow-left mr-2"></i>Back to Login
            </a>
        </div>
    `;
}

function escapeHtml(str) {
    return str.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}
