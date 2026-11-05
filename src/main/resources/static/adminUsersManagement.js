// =============================================================
//  adminUsersManagement.js — Admin User Management Interactivity
//  ArtsyVibe — Artisan Co-op Platform
// =============================================================
// Loaded AFTER adminDashboard.js. Does NOT duplicate sidebar,
// dropdown, or logout logic.
// =============================================================

(() => {
  const qs  = (sel, ctx = document) => ctx.querySelector(sel);
  const qsa = (sel, ctx = document) => Array.from(ctx.querySelectorAll(sel));

  document.addEventListener('DOMContentLoaded', () => {

    // ─── References ───
    const tbody        = qs('tbody');
    const searchInput  = qs('input[type="text"]');
    const selects      = qsa('select.form-input');
    const roleSelect   = selects[0];
    const statusSelect = selects[1];
    const paginationLabel = qs('.border-t p.text-sm');
    const paginationContainer = qs('.border-t .flex.items-center.gap-2');
    const bulkBar      = qs('#bulkActionBar');
    const selectedCountEl = qs('#selectedCount');
    let currentPanelRow = null;
    let currentPage = 1;
    const perPage = 10;

    // ─── Helpers ───
    function getRows() { return qsa('tr.table-row', tbody); }
    function getVisibleRows() { return getRows().filter(r => r.style.display !== 'none'); }

    function getNameText(row) {
      const el = qs('td:nth-child(2) span.font-medium', row);
      return el ? el.textContent.trim() : '';
    }
    function getEmailText(row) {
      const td = row.children[2];
      return td ? td.textContent.trim() : '';
    }
    function getRoleText(row) {
      const cell = row.children[3];
      const badge = cell ? qs('span', cell) : null;
      return badge ? badge.textContent.trim().toLowerCase() : '';
    }
    function getStatusText(row) {
      const cell = row.children[4];
      const badge = cell ? qs('span', cell) : null;
      return badge ? badge.textContent.trim().toLowerCase() : '';
    }
    function getJoinDate(row) { return row.children[5] ? row.children[5].textContent.trim() : ''; }
    function getLastActive(row) { return row.children[6] ? row.children[6].textContent.trim() : ''; }

    function renderPaginationControls(totalFiltered) {
      if (!paginationContainer) return;
      
      const totalPages = Math.ceil(totalFiltered / perPage) || 1;
      if (currentPage > totalPages) currentPage = totalPages;
      if (currentPage < 1) currentPage = 1;
      
      let html = '';
      
      // Previous button
      const prevDisabled = currentPage === 1 ? 'disabled opacity-50 cursor-not-allowed' : '';
      html += `<button class="px-4 py-2 border border-[#e5e0d8] rounded-lg text-[#8b7355] hover:border-[#c17c5f] hover:text-[#c17c5f] transition-colors ${prevDisabled}" data-page="prev">Previous</button>`;
      
      // Page numbers
      for (let i = 1; i <= totalPages; i++) {
        if (i === currentPage) {
          html += `<button class="px-4 py-2 bg-[#c17c5f] text-white rounded-lg font-medium" data-page="${i}">${i}</button>`;
        } else {
          html += `<button class="px-4 py-2 border border-[#e5e0d8] rounded-lg text-[#8b7355] hover:border-[#c17c5f] hover:text-[#c17c5f] transition-colors" data-page="${i}">${i}</button>`;
        }
      }
      
      // Next button
      const nextDisabled = currentPage === totalPages ? 'disabled opacity-50 cursor-not-allowed' : '';
      html += `<button class="px-4 py-2 border border-[#e5e0d8] rounded-lg text-[#8b7355] hover:border-[#c17c5f] hover:text-[#c17c5f] transition-colors ${nextDisabled}" data-page="next">Next</button>`;
      
      paginationContainer.innerHTML = html;
      
      // Wire up event listeners
      qsa('button', paginationContainer).forEach(btn => {
        btn.addEventListener('click', () => {
          const action = btn.dataset.page;
          if (!action || btn.disabled) return;
          
          if (action === 'prev') {
            if (currentPage > 1) {
              currentPage--;
              applyAllFilters(false);
            }
          } else if (action === 'next') {
            if (currentPage < totalPages) {
              currentPage++;
              applyAllFilters(false);
            }
          } else {
            const pageNum = parseInt(action);
            if (!isNaN(pageNum)) {
              currentPage = pageNum;
              applyAllFilters(false);
            }
          }
        });
      });
    }

    // ─────────────────────────────────────────────
    // SECTION 1 — Search Filter & Pagination Application
    // ─────────────────────────────────────────────
    function applyAllFilters(resetPage = true) {
      if (resetPage) currentPage = 1;
      
      const searchVal = (searchInput ? searchInput.value : '').toLowerCase();
      const roleVal   = roleSelect ? roleSelect.value.toLowerCase() : '';
      const statusVal = statusSelect ? statusSelect.value.toLowerCase() : '';

      const matchedRows = [];
      
      getRows().forEach(row => {
        const name   = getNameText(row).toLowerCase();
        const email  = getEmailText(row).toLowerCase();
        const role   = getRoleText(row);
        const status = getStatusText(row);

        let show = true;
        if (searchVal && !name.includes(searchVal) && !email.includes(searchVal)) show = false;
        if (roleVal && role !== roleVal) show = false;
        if (statusVal) {
          if (status !== statusVal && !(statusVal === 'pending' && status === 'pending approval')) {
            show = false;
          }
        }

        if (show) {
          matchedRows.push(row);
        } else {
          row.style.display = 'none';
        }
      });
      
      const totalFiltered = matchedRows.length;
      const totalPages = Math.ceil(totalFiltered / perPage) || 1;
      if (currentPage > totalPages) currentPage = totalPages;
      if (currentPage < 1) currentPage = 1;
      
      const startIndex = (currentPage - 1) * perPage;
      const endIndex = startIndex + perPage;
      
      matchedRows.forEach((row, index) => {
        if (index >= startIndex && index < endIndex) {
          row.style.display = '';
        } else {
          row.style.display = 'none';
        }
      });
      
      // Update pagination label
      const start = totalFiltered === 0 ? 0 : startIndex + 1;
      const end   = Math.min(endIndex, totalFiltered);
      if (paginationLabel) {
        paginationLabel.textContent = `Showing ${start}–${end} of ${totalFiltered} users`;
      }
      
      renderPaginationControls(totalFiltered);
    }

    if (searchInput) searchInput.addEventListener('input', applyAllFilters);

    // ─────────────────────────────────────────────
    // SECTION 2 — Role & Status Filters
    // ─────────────────────────────────────────────
    if (roleSelect) roleSelect.addEventListener('change', applyAllFilters);
    if (statusSelect) statusSelect.addEventListener('change', applyAllFilters);

    // ─────────────────────────────────────────────
    // SECTION 3 — Column Sorting
    // ─────────────────────────────────────────────
    const sortableThs = qsa('th', qs('thead'));
    const nameTh     = sortableThs[1];
    const emailTh    = sortableThs[2];
    const joinDateTh = sortableThs[5];
    const sortHeaders = [
      { th: nameTh,     idx: 1, type: 'text',  getter: getNameText },
      { th: emailTh,    idx: 2, type: 'text',  getter: getEmailText },
      { th: joinDateTh, idx: 5, type: 'date',  getter: r => getJoinDate(r) },
    ];
    const sortState = {};

    sortHeaders.forEach(({ th, idx, type, getter }) => {
      if (!th) return;
      th.style.cursor = 'pointer';
      sortState[idx] = 'none';

      th.addEventListener('click', () => {
        const dir = sortState[idx] === 'asc' ? 'desc' : 'asc';
        // Reset other icons
        sortHeaders.forEach(s => {
          if (s.th) {
            const icon = qs('i.fas', s.th);
            if (icon) { icon.className = 'fas fa-sort ml-2'; }
            if (s.idx !== idx) sortState[s.idx] = 'none';
          }
        });
        sortState[idx] = dir;

        const icon = qs('i.fas', th);
        if (icon) icon.className = dir === 'asc' ? 'fas fa-sort-up ml-2' : 'fas fa-sort-down ml-2';

        const rows = getRows();
        rows.sort((a, b) => {
          let va = getter(a);
          let vb = getter(b);
          if (type === 'date') {
            va = new Date(va).getTime() || 0;
            vb = new Date(vb).getTime() || 0;
            return dir === 'asc' ? va - vb : vb - va;
          }
          va = va.toLowerCase();
          vb = vb.toLowerCase();
          if (va < vb) return dir === 'asc' ? -1 : 1;
          if (va > vb) return dir === 'asc' ? 1 : -1;
          return 0;
        });
        rows.forEach(r => tbody.appendChild(r));
      });
    });

    // ─────────────────────────────────────────────
    // SECTION 4 — Suspend / Activate Toggle
    // ─────────────────────────────────────────────
    function toggleSuspend(row) {
      const userId = row.dataset.userId;
      if (!userId) return;

      const actionsDiv = qs('td:last-child .flex', row);
      const toggleBtn  = actionsDiv ? actionsDiv.children[1] : null;
      const statusCell = row.children[4];
      const statusBadge = statusCell ? qs('span', statusCell) : null;
      if (!toggleBtn || !statusBadge) return;

      // If button text is "Suspend", we want to suspend the user. Otherwise, activate them.
      const currentlyActive = toggleBtn.textContent.trim() === 'Suspend';
      const targetStatus = currentlyActive ? 'suspended' : 'active';

      fetch(`/api/admin/users/${userId}/status?status=${targetStatus}`, {
        method: 'POST'
      })
      .then(res => res.json())
      .then(data => {
        if (data.success) {
          if (targetStatus === 'active') {
            const rejectBtn = actionsDiv.querySelector('.reject-btn');
            if (rejectBtn) rejectBtn.remove();
            toggleBtn.textContent = 'Suspend';
            toggleBtn.className = 'px-3 py-1.5 bg-red-500 hover:bg-red-600 text-white text-xs rounded-lg font-medium transition-colors';
            statusBadge.className = 'status-badge status-active px-3 py-1 rounded-full text-xs font-semibold';
            statusBadge.textContent = 'Active';
            row.classList.remove('suspended');
            if (window.showToast) window.showToast('User activated successfully', 'success');
          } else {
            toggleBtn.textContent = 'Activate';
            toggleBtn.className = 'px-3 py-1.5 bg-green-500 hover:bg-green-600 text-white text-xs rounded-lg font-medium transition-colors';
            statusBadge.className = 'status-badge status-suspended px-3 py-1 rounded-full text-xs font-semibold';
            statusBadge.textContent = 'Suspended';
            row.classList.add('suspended');
            if (window.showToast) window.showToast('User suspended successfully', 'warning');
          }
        } else {
          if (window.showToast) window.showToast(data.message || 'Operation failed', 'error');
        }
      })
      .catch(err => {
        console.error(err);
        if (window.showToast) window.showToast('Error connecting to server', 'error');
      });
    }

    function wireToggleButtons() {
      getRows().forEach(row => {
        const actionsDiv = qs('td:last-child .flex', row);
        const toggleBtn = actionsDiv ? actionsDiv.children[1] : null;
        if (!toggleBtn || toggleBtn.dataset.wiredToggle) return;
        toggleBtn.dataset.wiredToggle = '1';
        toggleBtn.addEventListener('click', e => {
          e.stopPropagation();
          toggleSuspend(row);
        });
      });
    }

    function rejectPendingUser(row) {
      const userId = row.dataset.userId;
      if (!userId) return;
      fetch(`/api/admin/users/${userId}/status?status=rejected`, {
        method: 'POST'
      })
      .then(res => res.json())
      .then(data => {
        if (data.success) {
          const statusCell = row.children[4];
          const statusBadge = statusCell ? qs('span', statusCell) : null;
          const actionsDiv = qs('td:last-child .flex', row);
          const approveBtn = actionsDiv ? actionsDiv.querySelector('.approve-btn') : null;
          const rejectBtn = actionsDiv ? actionsDiv.querySelector('.reject-btn') : null;
          if (statusBadge) {
            statusBadge.textContent = 'Rejected';
            statusBadge.className = 'status-badge status-rejected px-3 py-1 rounded-full text-xs font-semibold';
          }
          if (approveBtn) {
            approveBtn.textContent = 'Approve';
            approveBtn.className = 'approve-btn px-3 py-1.5 bg-green-500 hover:bg-green-600 text-white text-xs rounded-lg font-medium transition-colors';
          }
          if (rejectBtn) {
            rejectBtn.remove();
          }
          if (window.showToast) window.showToast('User registration request rejected', 'warning');
        } else {
          if (window.showToast) window.showToast(data.message || 'Operation failed', 'error');
        }
      })
      .catch(err => {
        console.error(err);
        if (window.showToast) window.showToast('Error connecting to server', 'error');
      });
    }

    function wireRejectButtons() {
      getRows().forEach(row => {
        const actionsDiv = qs('td:last-child .flex', row);
        const rejectBtn = actionsDiv ? actionsDiv.querySelector('.reject-btn') : null;
        if (!rejectBtn || rejectBtn.dataset.wiredReject) return;
        rejectBtn.dataset.wiredReject = '1';
        rejectBtn.addEventListener('click', e => {
          e.stopPropagation();
          rejectPendingUser(row);
        });
      });
    }

    wireToggleButtons();
    wireRejectButtons();

    // ─────────────────────────────────────────────
    // SECTION 5 — Edit Button (Inline Editing)
    // ─────────────────────────────────────────────
    function wireEditButtons() {
      getRows().forEach(row => {
        const actionsDiv = qs('td:last-child .flex', row);
        const editBtn = actionsDiv ? actionsDiv.children[1] : null;
        if (!editBtn || editBtn.dataset.wiredEdit) return;
        editBtn.dataset.wiredEdit = '1';

        editBtn.addEventListener('click', e => {
          e.stopPropagation();
          const icon = qs('i', editBtn);
          if (!icon) return;

          const nameTd  = row.children[1];
          const emailTd = row.children[2];

          // Already in edit mode? (save)
          if (icon.classList.contains('fa-save')) {
            const nameInput  = qs('input', nameTd);
            const emailInput = qs('input', emailTd);
            const newName  = nameInput ? nameInput.value.trim() : '';
            const newEmail = emailInput ? emailInput.value.trim() : '';
            const userId = row.dataset.userId;

            if (userId) {
              fetch(`/api/admin/users/${userId}/update?name=${encodeURIComponent(newName)}&email=${encodeURIComponent(newEmail)}`, {
                method: 'POST'
              })
              .then(res => res.json())
              .then(data => {
                if (!data.success) {
                  if (window.showToast) window.showToast(data.message || 'Failed to update user in database', 'error');
                }
              })
              .catch(err => {
                console.error(err);
                if (window.showToast) window.showToast('Error saving user to database', 'error');
              });
            }

            // Restore name cell
            const nameSpan = qs('span.font-medium', nameTd) || nameTd.querySelector('div span.font-medium');
            if (nameInput) {
              nameInput.replaceWith(document.createTextNode(''));
              const container = qs('div.flex', nameTd);
              const existing = qs('span.font-medium', container);
              if (existing) { existing.textContent = newName; }
              else {
                const sp = document.createElement('span');
                sp.className = 'font-medium text-[#5c4a3d]';
                sp.textContent = newName;
                container.appendChild(sp);
              }
            }

            // Restore email cell
            if (emailInput) {
              emailTd.textContent = newEmail;
              emailTd.className = 'px-6 py-4 text-[#8b7355]';
            }

            // Update initials
            const initialsEl = qs('div.w-9 span', nameTd);
            if (initialsEl && newName) {
              const parts = newName.split(/\s+/);
              initialsEl.textContent = (parts[0]?.[0] || '') + (parts[parts.length - 1]?.[0] || '');
            }

            icon.classList.remove('fa-save');
            icon.classList.add('fa-edit');

            // Remove cancel button
            const cancelBtn = qs('[data-cancel-btn]', actionsDiv);
            if (cancelBtn) cancelBtn.remove();

            if (window.showToast) window.showToast('User updated successfully', 'success');
            return;
          }

          // Enter edit mode
          const currentName  = getNameText(row);
          const currentEmail = getEmailText(row);

          // Replace name with input
          const nameContainer = qs('div.flex', nameTd);
          const nameSpan = qs('span.font-medium', nameContainer);
          if (nameSpan) {
            const input = document.createElement('input');
            input.type = 'text';
            input.value = currentName;
            input.className = 'form-input';
            input.style.cssText = 'border: 1px solid #e5e0d8; border-radius: 6px; padding: 4px 8px; width: 140px;';
            nameSpan.replaceWith(input);
          }

          // Replace email with input
          const emailInput = document.createElement('input');
          emailInput.type = 'text';
          emailInput.value = currentEmail;
          emailInput.className = 'form-input';
          emailInput.style.cssText = 'border: 1px solid #e5e0d8; border-radius: 6px; padding: 4px 8px; width: 180px;';
          emailTd.textContent = '';
          emailTd.appendChild(emailInput);

          // Change edit icon to save
          icon.classList.remove('fa-edit');
          icon.classList.add('fa-save');

          // Add cancel button
          const cancelBtn = document.createElement('button');
          cancelBtn.className = 'p-2 text-red-500 hover:text-red-700 transition-colors';
          cancelBtn.dataset.cancelBtn = '1';
          cancelBtn.innerHTML = '<i class="fas fa-times"></i>';
          cancelBtn.addEventListener('click', ev => {
            ev.stopPropagation();
            // Restore name
            const ni = qs('input', nameTd);
            if (ni) {
              const sp = document.createElement('span');
              sp.className = 'font-medium text-[#5c4a3d]';
              sp.textContent = currentName;
              ni.replaceWith(sp);
            }
            // Restore email
            emailTd.textContent = currentEmail;
            emailTd.className = 'px-6 py-4 text-[#8b7355]';

            icon.classList.remove('fa-save');
            icon.classList.add('fa-edit');
            cancelBtn.remove();
          });

          editBtn.insertAdjacentElement('afterend', cancelBtn);
        });
      });
    }
    wireEditButtons();

    // ─────────────────────────────────────────────
    // SECTION 6 — View Button → Side Panel Population
    // ─────────────────────────────────────────────
    const panel = qs('#userPanel');

    function wireViewButtons() {
      getRows().forEach(row => {
        const viewBtn = qs('td:last-child .flex button:first-child', row);
        if (!viewBtn || viewBtn.dataset.wiredView) return;
        viewBtn.dataset.wiredView = '1';
        // Remove inline onclick
        viewBtn.removeAttribute('onclick');

        viewBtn.addEventListener('click', e => {
          e.stopPropagation();
          currentPanelRow = row;

          const name     = getNameText(row);
          const email    = getEmailText(row);
          const role     = getRoleText(row);
          const status   = getStatusText(row);
          const joinDate = getJoinDate(row);
          const lastAct  = getLastActive(row);
          const parts    = name.split(/\s+/);
          const initials = (parts[0]?.[0] || '') + (parts[parts.length - 1]?.[0] || '');

          if (panel) {
            // Initials
            const initialsEl = qs('.side-panel .w-20 span', panel);
            if (initialsEl) initialsEl.textContent = initials.toUpperCase();

            // Name & email
            const nameEl  = qs('.side-panel h3', panel);
            const emailEl = qs('.side-panel h3 + p', panel);
            if (nameEl) nameEl.textContent = name;
            if (emailEl) emailEl.textContent = email;

            // Role badge
            const roleBadge = qs('.side-panel .bg-\\[\\#f5ebe0\\] [class*="role-"]', panel);
            if (roleBadge) {
              roleBadge.className = `status-badge role-${role} px-3 py-1 rounded-full text-xs font-semibold`;
              roleBadge.textContent = role.charAt(0).toUpperCase() + role.slice(1);
            }

            // Status badge
            const statusBadge = qs('.side-panel .bg-\\[\\#f5ebe0\\] [class*="status-"]', panel);
            if (statusBadge) {
              const normalizedStatus = status === 'pending approval' ? 'pending' : status;
              statusBadge.className = `status-badge status-${normalizedStatus} px-3 py-1 rounded-full text-xs font-semibold`;
              statusBadge.textContent = status.charAt(0).toUpperCase() + status.slice(1);
            }

            // Account info rows
            const infoRows = qsa('.space-y-3 .flex.justify-between', panel);
            if (infoRows[1]) { // Join Date row
              const val = qs('span:last-child', infoRows[1]);
              if (val) val.textContent = joinDate;
            }
            if (infoRows[2]) { // Last Active row
              const val = qs('span:last-child', infoRows[2]);
              if (val) val.textContent = lastAct;
            }

            // Update suspend button state
            if (suspendBtn) {
              if (status === 'suspended') {
                suspendBtn.innerHTML = '<i class="fas fa-check-circle"></i> Activate Account';
                suspendBtn.className = 'w-full bg-green-500 hover:bg-green-600 text-white py-3 rounded-lg font-semibold transition-colors flex items-center justify-center gap-2';
              } else {
                suspendBtn.innerHTML = '<i class="fas fa-ban"></i> Suspend Account';
                suspendBtn.className = 'w-full bg-red-500 hover:bg-red-600 text-white py-3 rounded-lg font-semibold transition-colors flex items-center justify-center gap-2';
              }
            }
          }

          // Open panel
          if (typeof window.openUserPanel === 'function') {
            window.openUserPanel();
          } else {
            if (panel) panel.classList.add('open');
            const overlay = qs('#overlay');
            if (overlay) overlay.classList.add('open');
            document.body.style.overflow = 'hidden';
          }
        });
      });
    }
    wireViewButtons();

    // ─────────────────────────────────────────────
    // SECTION 7 — Side Panel Quick Actions
    // ─────────────────────────────────────────────
    const quickActionBtns = qsa('.side-panel .space-y-3 button', panel);
    const suspendBtn   = quickActionBtns[0];
    const resetPwdBtn  = quickActionBtns[1];
    const deleteAccBtn = quickActionBtns[2];

    if (suspendBtn) {
      suspendBtn.addEventListener('click', () => {
        if (currentPanelRow) {
          const actionText = suspendBtn.textContent.trim().toLowerCase();
          const confirmed = window.confirm(`Are you sure you want to ${actionText}?`);
          if (confirmed) {
            toggleSuspend(currentPanelRow);
            setTimeout(() => {
              const newStatus = getStatusText(currentPanelRow);
              const statusBadge = qs('.side-panel .bg-\\[\\#f5ebe0\\] [class*="status-"]', panel);
              if (statusBadge) {
                statusBadge.className = `status-badge status-${newStatus} px-3 py-1 rounded-full text-xs font-semibold`;
                statusBadge.textContent = newStatus.charAt(0).toUpperCase() + newStatus.slice(1);
              }
              if (newStatus === 'suspended') {
                suspendBtn.innerHTML = '<i class="fas fa-check-circle"></i> Activate Account';
                suspendBtn.className = 'w-full bg-green-500 hover:bg-green-600 text-white py-3 rounded-lg font-semibold transition-colors flex items-center justify-center gap-2';
              } else {
                suspendBtn.innerHTML = '<i class="fas fa-ban"></i> Suspend Account';
                suspendBtn.className = 'w-full bg-red-500 hover:bg-red-600 text-white py-3 rounded-lg font-semibold transition-colors flex items-center justify-center gap-2';
              }
            }, 300);
          }
        }
      });
    }

    if (resetPwdBtn) {
      resetPwdBtn.addEventListener('click', () => {
        if (window.confirm('Send a password reset email to this user?')) {
          if (window.showToast) window.showToast('Password reset email sent', 'success');
        }
      });
    }

    if (deleteAccBtn) {
      deleteAccBtn.addEventListener('click', () => {
        if (currentPanelRow) {
          const userId = currentPanelRow.dataset.userId;
          if (!userId) return;
          if (window.confirm('This action is permanent. Are you sure you want to delete this account?')) {
            fetch(`/api/admin/users/${userId}/delete`, {
              method: 'POST'
            })
            .then(res => res.json())
            .then(data => {
              if (data.success) {
                currentPanelRow.remove();
                currentPanelRow = null;
                updatePaginationLabel();
                if (window.showToast) window.showToast('Account deleted successfully', 'error');
                if (typeof window.closeUserPanel === 'function') window.closeUserPanel();
              } else {
                if (window.showToast) window.showToast(data.message || 'Delete failed', 'error');
              }
            })
            .catch(err => {
              console.error(err);
              if (window.showToast) window.showToast('Error deleting account', 'error');
            });
          }
        }
      });
    }

    // ─────────────────────────────────────────────
    // SECTION 8 — Bulk Actions
    // ─────────────────────────────────────────────
    const bulkBtns = qsa('#bulkActionBar .flex.items-center.gap-3 button');
    const bulkSuspendBtn = bulkBtns[0];
    const bulkDeleteBtn  = bulkBtns[1];

    function getCheckedRows() {
      return qsa('.user-checkbox:checked').map(cb => cb.closest('tr'));
    }

    function uncheckAll() {
      qsa('.user-checkbox').forEach(cb => { cb.checked = false; });
      const selectAll = qs('thead input[type="checkbox"]');
      if (selectAll) selectAll.checked = false;
      if (bulkBar) bulkBar.classList.remove('visible');
      if (selectedCountEl) selectedCountEl.textContent = '0';
    }

    if (bulkSuspendBtn) {
      bulkSuspendBtn.addEventListener('click', () => {
        const rows = getCheckedRows();
        if (!rows.length) return;

        const isActivate = bulkSuspendBtn.textContent.trim() === 'Activate Selected';
        const confirmMsg = isActivate ? 'Are you sure you want to activate all selected users?' : 'Are you sure you want to suspend all selected users?';
        if (!window.confirm(confirmMsg)) return;

        rows.forEach(row => {
          const toggleBtn = qs('td:last-child .flex', row)?.children[1];
          if (!toggleBtn) return;
          const btnText = toggleBtn.textContent.trim().toLowerCase();
          if (isActivate && btnText === 'activate') {
            toggleSuspend(row);
          } else if (!isActivate && btnText === 'suspend') {
            toggleSuspend(row);
          }
        });
        uncheckAll();
        if (window.showToast) {
          const toastMsg = isActivate ? `${rows.length} users activated successfully` : `${rows.length} users suspended successfully`;
          const toastType = isActivate ? 'success' : 'warning';
          window.showToast(toastMsg, toastType);
        }
      });
    }

    if (bulkDeleteBtn) {
      bulkDeleteBtn.addEventListener('click', () => {
        const rows = getCheckedRows();
        if (!rows.length) return;
        if (!window.confirm('Permanently delete all selected users?')) return;

        const count = rows.length;
        rows.forEach(r => {
          const userId = r.dataset.userId;
          if (userId) {
            fetch(`/api/admin/users/${userId}/delete`, { method: 'POST' });
          }
          r.remove();
        });
        uncheckAll();
        applyAllFilters(false);
        if (window.showToast) window.showToast(`${count} users deleted`, 'error');
      });
    }

    // ─────────────────────────────────────────────
    // SECTION 9 — Export Button
    // ─────────────────────────────────────────────
    const exportBtn = qs('button.bg-\\[\\#c17c5f\\]');
    if (exportBtn && exportBtn.textContent.trim().includes('Export')) {
      exportBtn.addEventListener('click', () => {
        const rows = getVisibleRows();
        let csv = 'Name,Email,Role,Status,Join Date,Last Active\n';

        rows.forEach(row => {
          const name    = getNameText(row).replace(/,/g, ' ');
          const email   = getEmailText(row);
          const role    = getRoleText(row);
          const status  = getStatusText(row);
          const joinDt  = getJoinDate(row);
          const lastAct = getLastActive(row);
          csv += `"${name}","${email}","${role}","${status}","${joinDt}","${lastAct}"\n`;
        });

        const blob = new Blob([csv], { type: 'text/csv' });
        const url  = URL.createObjectURL(blob);
        const a    = document.createElement('a');
        a.href = url;
        a.download = 'artisyvibe-users-export.csv';
        a.click();
        URL.revokeObjectURL(url);
        if (window.showToast) window.showToast('Export downloaded successfully', 'success');
      });
    }

    // ─────────────────────────────────────────────
    // SECTION 10 — Pagination Initializer
    // ─────────────────────────────────────────────
    applyAllFilters(true);

  }); // end DOMContentLoaded
})();
