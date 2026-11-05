document.addEventListener('DOMContentLoaded', () => {

    // Login state elements
    const loginButtonWrapper = document.getElementById('login-button-wrapper');
    const userSection = document.getElementById('user-section');
    const navUserName = document.getElementById('nav-user-name');
    const navUserEmail = document.getElementById('nav-user-email');
    const notificationBadge = document.getElementById('notification-badge');
    const cartBadge = document.getElementById('cart-badge');
    const notificationsList = document.querySelector('.space-y-4');
    const emptyState = document.getElementById('empty-state');

    function updateLoginState() {
        const loggedIn = sessionStorage.getItem('isLoggedIn') === 'true' || sessionStorage.getItem('loggedIn') === 'true';
        if (loggedIn) {
            loginButtonWrapper?.classList.add('hidden');
            userSection?.classList.remove('hidden');

            const userName = sessionStorage.getItem('userName') || 'John Doe';
            const userEmail = sessionStorage.getItem('userEmail') || 'john@example.com';
            if (navUserName) navUserName.textContent = userName;
            if (navUserEmail) navUserEmail.textContent = userEmail;

            fetchNotifications();
        } else {
            loginButtonWrapper?.classList.remove('hidden');
            userSection?.classList.add('hidden');
            if (notificationsList) notificationsList.innerHTML = '';
            emptyState?.classList.remove('hidden');
        }

        updateCartBadge();
    }

    async function fetchNotifications() {
        const userId = sessionStorage.getItem('userId');
        if (!userId) return;

        try {
            const res = await fetch(`/api/notifications/${userId}`);
            if (!res.ok) throw new Error('Failed to fetch');
            const notifications = await res.json();
            renderNotifications(notifications);
            updateBadgeCount(notifications.filter(n => !n.read).length);
        } catch (err) {
            console.error('Error fetching notifications:', err);
        }
    }

    function renderNotifications(notifications) {
        if (!notificationsList) return;

        if (notifications.length === 0) {
            notificationsList.innerHTML = '';
            emptyState?.classList.remove('hidden');
            return;
        }

        emptyState?.classList.add('hidden');
        notificationsList.innerHTML = notifications.map(n => `
            <div class="notification-card ${n.read ? 'read' : 'unread'} p-4 rounded-xl border border-gray-100 bg-white shadow-sm transition-all hover:shadow-md cursor-pointer group"
                 data-id="${n.id}" data-type="${n.type}" data-read="${n.read}" onclick="handleNotificationClick(this)">
                <div class="flex items-start gap-4">
                    ${!n.read ? '<div class="unread-dot mt-1.5"></div>' : '<div class="w-2 mt-1.5"></div>'}
                    <div class="flex-1">
                        <div class="flex justify-between items-start mb-1">
                            <h3 class="font-semibold text-[#2d3436] group-hover:text-[#c17c5f] transition-colors">${n.title}</h3>
                            <span class="text-xs text-gray-400">${new Date(n.timestamp).toLocaleString()}</span>
                        </div>
                        <p class="text-sm text-gray-600 line-clamp-2">${n.message}</p>
                        <div class="mt-2 flex items-center gap-3">
                            <span class="px-2 py-0.5 rounded-full text-[10px] font-medium bg-gray-100 text-gray-500 uppercase tracking-wider">${n.type.replace('_', ' ')}</span>
                            ${n.link ? `<a href="${n.link}" class="text-[10px] font-semibold text-[#c17c5f] hover:underline">View Details</a>` : ''}
                        </div>
                    </div>
                    <button onclick="event.stopPropagation(); deleteNotification(${n.id}, this.closest('.notification-card'))" 
                            class="text-gray-300 hover:text-red-500 transition-colors p-1">
                        <i class="fas fa-trash-alt text-sm"></i>
                    </button>
                </div>
            </div>
        `).join('');
    }

    window.handleNotificationClick = async function(card) {
        const id = card.getAttribute('data-id');
        const isRead = card.getAttribute('data-read') === 'true';
        
        if (!isRead) {
            try {
                await fetch(`/api/notifications/${id}/read`, { method: 'PUT' });
                card.setAttribute('data-read', 'true');
                card.classList.remove('unread');
                card.classList.add('read');
                const dot = card.querySelector('.unread-dot');
                if (dot) {
                    const spacer = document.createElement('div');
                    spacer.className = 'w-2 mt-1.5';
                    dot.replaceWith(spacer);
                }
                const currentCount = parseInt(sessionStorage.getItem('notificationCount') || '0');
                updateBadgeCount(Math.max(0, currentCount - 1));
            } catch (err) {
                console.error('Error marking as read:', err);
            }
        }
        
        const type = card.getAttribute('data-type');
        // Optional navigation logic based on type could go here
    };

    window.deleteNotification = async function(id, cardElement) {
        try {
            const res = await fetch(`/api/notifications/${id}`, { method: 'DELETE' });
            if (res.ok) {
                cardElement.style.transform = 'translateX(20px)';
                cardElement.style.opacity = '0';
                setTimeout(() => {
                    cardElement.remove();
                    if (document.querySelectorAll('.notification-card').length === 0) {
                        emptyState?.classList.remove('hidden');
                    }
                    const isUnread = cardElement.getAttribute('data-read') === 'false';
                    if (isUnread) {
                        const currentCount = parseInt(sessionStorage.getItem('notificationCount') || '0');
                        updateBadgeCount(Math.max(0, currentCount - 1));
                    }
                }, 300);
                showToast('Notification deleted', 'success');
            }
        } catch (err) {
            console.error('Error deleting notification:', err);
        }
    };

    window.markAllAsRead = async function() {
        const userId = sessionStorage.getItem('userId');
        if (!userId) return;

        try {
            const res = await fetch(`/api/notifications/read-all/${userId}`, { method: 'PUT' });
            if (res.ok) {
                document.querySelectorAll('.notification-card[data-read="false"]').forEach(card => {
                    card.setAttribute('data-read', 'true');
                    card.classList.remove('unread');
                    card.classList.add('read');
                    const dot = card.querySelector('.unread-dot');
                    if (dot) {
                        const spacer = document.createElement('div');
                        spacer.className = 'w-2 mt-1.5';
                        dot.replaceWith(spacer);
                    }
                });
                updateBadgeCount(0);
                showToast('All notifications marked as read', 'success');
            }
        } catch (err) {
            console.error('Error marking all as read:', err);
        }
    };

    window.filterNotifications = function(button, type) {
        document.querySelectorAll('.filter-pill').forEach(pill => pill.classList.remove('active'));
        button.classList.add('active');

        const cards = document.querySelectorAll('.notification-card');
        let visibleCount = 0;

        cards.forEach(card => {
            const cardType = card.getAttribute('data-type');
            if (type === 'all' || cardType.toLowerCase().includes(type.toLowerCase())) {
                card.style.display = 'block';
                visibleCount++;
            } else {
                card.style.display = 'none';
            }
        });

        if (visibleCount === 0) emptyState?.classList.remove('hidden');
        else emptyState?.classList.add('hidden');
    };

    function updateBadgeCount(count) {
        sessionStorage.setItem('notificationCount', count.toString());
        if (typeof window.updateNotificationBadge === 'function') {
            window.updateNotificationBadge();
        }
    }

    function showToast(message, type = 'info') {
        if (!document.getElementById('toast-styles')) {
            const style = document.createElement('style');
            style.id = 'toast-styles';
            style.innerHTML = `
                .toast-container { position: fixed; bottom: 20px; right: 20px; z-index: 9999; display: flex; flex-direction: column; gap: 10px; }
                .toast { padding: 12px 24px; border-radius: 8px; color: white; font-family: 'Inter', sans-serif; box-shadow: 0 4px 12px rgba(0,0,0,0.15); animation: slideIn 0.3s ease-out; display: flex; align-items: center; gap: 10px; min-width: 250px; }
                .toast.success { background-color: #10b981; border-left: 5px solid #059669; }
                .toast.error { background-color: #dc2626; border-left: 5px solid #991b1b; }
                .toast.info { background-color: #2563eb; border-left: 5px solid #1d4ed8; }
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
        toast.innerHTML = `<i class="fas fa-${type === 'success' ? 'check-circle' : 'info-circle'}"></i> <span>${message}</span>`;
        container.appendChild(toast);
        setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, 3000);
    }

    updateLoginState();
});

function updateCartBadge() {
    const count = sessionStorage.getItem('cartCount') || '0';
    const badges = document.querySelectorAll('#cart-badge');
    badges.forEach(b => {
        b.textContent = count;
        b.style.display = count !== '0' ? 'flex' : 'none';
    });
}
