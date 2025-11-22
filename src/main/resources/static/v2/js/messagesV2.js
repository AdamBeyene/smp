/**
 * Messages V2 Page - JavaScript Functionality
 * Provides table functionality, search, pagination, sorting, and message actions
 */

(function() {
    'use strict';

    // Global state
    const state = {
        messages: window.messagesData || [],
        filteredMessages: [],
        currentPage: 1,
        pageSize: 100,
        sortColumn: null,
        sortDirection: 'asc',
        autoRefreshInterval: null,
        searchTerm: ''
    };

    /**
     * Initialize page functionality when DOM is ready
     */
    function init() {
        console.log('🚀 Initializing Messages V2 page...');

        // Initialize state from data
        state.filteredMessages = [...state.messages];

        // Setup event handlers
        setupSearch();
        setupPagination();
        setupSorting();
        setupRefresh();
        setupAdvancedSearch();
        setupMessageActions();
        setupExport();

        // Initial render
        renderTable();
        updateResultsInfo();

        console.log(`✅ Messages V2 initialized with ${state.messages.length} messages`);
    }

    /**
     * Setup search functionality
     */
    function setupSearch() {
        const searchInput = document.getElementById('search-input');
        if (!searchInput) return;

        searchInput.addEventListener('input', (e) => {
            state.searchTerm = e.target.value.toLowerCase().trim();
            state.currentPage = 1; // Reset to first page
            filterMessages();
            renderTable();
            updateResultsInfo();
        });
    }

    /**
     * Filter messages based on search term
     */
    function filterMessages() {
        if (!state.searchTerm) {
            state.filteredMessages = [...state.messages];
            return;
        }

        state.filteredMessages = state.messages.filter(msg => {
            return (
                (msg.text && msg.text.toLowerCase().includes(state.searchTerm)) ||
                (msg.from && msg.from.toLowerCase().includes(state.searchTerm)) ||
                (msg.to && msg.to.toLowerCase().includes(state.searchTerm)) ||
                (msg.id && msg.id.toLowerCase().includes(state.searchTerm)) ||
                (msg.providerId && msg.providerId.toLowerCase().includes(state.searchTerm))
            );
        });
    }

    /**
     * Setup pagination controls
     */
    function setupPagination() {
        // Page size selector
        const pageSizeSelect = document.getElementById('page-size');
        if (pageSizeSelect) {
            pageSizeSelect.addEventListener('change', (e) => {
                state.pageSize = parseInt(e.target.value);
                state.currentPage = 1;
                renderTable();
                updateResultsInfo();
            });
        }

        // Pagination buttons
        const firstPageBtn = document.getElementById('first-page');
        const prevPageBtn = document.getElementById('prev-page');
        const nextPageBtn = document.getElementById('next-page');
        const lastPageBtn = document.getElementById('last-page');

        if (firstPageBtn) {
            firstPageBtn.addEventListener('click', () => {
                state.currentPage = 1;
                renderTable();
                updateResultsInfo();
            });
        }

        if (prevPageBtn) {
            prevPageBtn.addEventListener('click', () => {
                if (state.currentPage > 1) {
                    state.currentPage--;
                    renderTable();
                    updateResultsInfo();
                }
            });
        }

        if (nextPageBtn) {
            nextPageBtn.addEventListener('click', () => {
                const totalPages = Math.ceil(state.filteredMessages.length / state.pageSize);
                if (state.currentPage < totalPages) {
                    state.currentPage++;
                    renderTable();
                    updateResultsInfo();
                }
            });
        }

        if (lastPageBtn) {
            lastPageBtn.addEventListener('click', () => {
                const totalPages = Math.ceil(state.filteredMessages.length / state.pageSize);
                state.currentPage = totalPages;
                renderTable();
                updateResultsInfo();
            });
        }
    }

    /**
     * Setup column sorting
     */
    function setupSorting() {
        const sortableHeaders = document.querySelectorAll('th.sortable');

        sortableHeaders.forEach(header => {
            header.addEventListener('click', () => {
                const column = header.dataset.sort;

                // Toggle sort direction if same column, otherwise default to ascending
                if (state.sortColumn === column) {
                    state.sortDirection = state.sortDirection === 'asc' ? 'desc' : 'asc';
                } else {
                    state.sortColumn = column;
                    state.sortDirection = 'asc';
                }

                // Update sort icons
                updateSortIcons();

                // Sort and render
                sortMessages();
                renderTable();
            });
        });
    }

    /**
     * Update sort icons on headers
     */
    function updateSortIcons() {
        const sortableHeaders = document.querySelectorAll('th.sortable');

        sortableHeaders.forEach(header => {
            const icon = header.querySelector('.sort-icon');
            if (!icon) return;

            const column = header.dataset.sort;

            if (column === state.sortColumn) {
                icon.className = `fas fa-sort-${state.sortDirection === 'asc' ? 'up' : 'down'} sort-icon`;
            } else {
                icon.className = 'fas fa-sort sort-icon';
            }
        });
    }

    /**
     * Sort messages based on current sort column and direction
     */
    function sortMessages() {
        if (!state.sortColumn) return;

        state.filteredMessages.sort((a, b) => {
            let aVal = a[state.sortColumn];
            let bVal = b[state.sortColumn];

            // Handle null/undefined
            if (aVal === null || aVal === undefined) aVal = '';
            if (bVal === null || bVal === undefined) bVal = '';

            // Convert to strings for comparison
            aVal = String(aVal).toLowerCase();
            bVal = String(bVal).toLowerCase();

            if (aVal < bVal) return state.sortDirection === 'asc' ? -1 : 1;
            if (aVal > bVal) return state.sortDirection === 'asc' ? 1 : -1;
            return 0;
        });
    }

    /**
     * Setup refresh functionality
     */
    function setupRefresh() {
        const refreshBtn = document.getElementById('refresh-btn');
        const autoRefreshSelect = document.getElementById('auto-refresh');

        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => {
                refreshMessages();
            });
        }

        if (autoRefreshSelect) {
            autoRefreshSelect.addEventListener('change', (e) => {
                const interval = parseInt(e.target.value);

                // Clear existing interval
                if (state.autoRefreshInterval) {
                    clearInterval(state.autoRefreshInterval);
                    state.autoRefreshInterval = null;
                }

                // Set new interval if not manual
                if (interval > 0) {
                    state.autoRefreshInterval = setInterval(() => {
                        refreshMessages();
                    }, interval);
                    console.log(`🔄 Auto-refresh enabled: ${interval / 1000}s`);
                }
            });
        }
    }

    /**
     * Refresh messages from server
     */
    function refreshMessages() {
        console.log('🔄 Refreshing messages...');
        const refreshBtn = document.getElementById('refresh-btn');

        // Add spinning animation
        if (refreshBtn) {
            const icon = refreshBtn.querySelector('i');
            if (icon) icon.classList.add('fa-spin');
        }

        // Reload page to get fresh data
        window.location.reload();
    }

    /**
     * Setup advanced search toggle and functionality
     */
    function setupAdvancedSearch() {
        const advSearchToggle = document.getElementById('advanced-search-toggle');
        const advSearchBtn = document.getElementById('adv-search-btn');
        const advSearchPanel = document.getElementById('advanced-search-panel');

        if (advSearchToggle && advSearchPanel) {
            advSearchToggle.addEventListener('click', () => {
                const isVisible = advSearchPanel.style.display !== 'none';
                advSearchPanel.style.display = isVisible ? 'none' : 'block';
            });
        }

        if (advSearchBtn) {
            advSearchBtn.addEventListener('click', () => {
                performAdvancedSearch();
            });
        }
    }

    /**
     * Perform advanced search with all criteria
     */
    function performAdvancedSearch() {
        const text = document.getElementById('adv-search-text')?.value || '';
        const from = document.getElementById('adv-search-from')?.value || '';
        const to = document.getElementById('adv-search-to')?.value || '';
        const direction = document.getElementById('adv-search-direction')?.value || 'Any';
        const encoding = document.getElementById('adv-search-encoding')?.value || '';
        const providerId = document.getElementById('adv-search-provider')?.value || '';

        // Build query parameters
        const params = new URLSearchParams();
        if (text) params.append('text', text);
        if (from) params.append('from', from);
        if (to) params.append('to', to);
        if (direction && direction !== 'Any') params.append('direction', direction);
        if (encoding) params.append('encoding', encoding);
        if (providerId) params.append('providerId', providerId);

        // Call advanced search API
        showActionResponse('Searching...', 'info');

        fetch(`/sim/messages/advanced-search?${params.toString()}`)
            .then(response => {
                if (!response.ok) throw new Error('Advanced search failed');
                return response.json();
            })
            .then(data => {
                state.messages = data;
                state.filteredMessages = [...data];
                state.currentPage = 1;
                renderTable();
                updateResultsInfo();
                showActionResponse(`Found ${data.length} messages`, 'success');
            })
            .catch(error => {
                console.error('Advanced search error:', error);
                showActionResponse('Advanced search failed: ' + error.message, 'error');
            });
    }

    /**
     * Setup message action buttons
     */
    function setupMessageActions() {
        // Delete All Messages
        const deleteAllBtn = document.getElementById('delete-all-btn');
        if (deleteAllBtn) {
            deleteAllBtn.addEventListener('click', () => {
                if (confirm('Are you sure you want to delete ALL messages? This cannot be undone.')) {
                    deleteAllMessages();
                }
            });
        }

        // Send Message
        const sendMsgBtn = document.getElementById('send-msg-btn');
        if (sendMsgBtn) {
            sendMsgBtn.addEventListener('click', () => {
                sendMessage();
            });
        }

        // Send Delivery Receipt
        const sendDrBtn = document.getElementById('send-dr-btn');
        if (sendDrBtn) {
            sendDrBtn.addEventListener('click', () => {
                sendDeliveryReceipt();
            });
        }

        // Group Concat Messages
        const groupConcatBtn = document.getElementById('group-concat-btn');
        if (groupConcatBtn) {
            groupConcatBtn.addEventListener('click', () => {
                groupConcatMessages();
            });
        }
    }

    /**
     * Delete all messages
     */
    function deleteAllMessages() {
        showActionResponse('Deleting all messages...', 'info');

        fetch('/sim/deleteAllMessages', {
            method: 'DELETE'
        })
            .then(response => {
                if (!response.ok) throw new Error('Delete failed');
                return response.text();
            })
            .then(result => {
                showActionResponse('All messages deleted successfully', 'success');
                // Refresh after 1 second
                setTimeout(() => {
                    refreshMessages();
                }, 1000);
            })
            .catch(error => {
                console.error('Delete error:', error);
                showActionResponse('Failed to delete messages: ' + error.message, 'error');
            });
    }

    /**
     * Send message
     */
    function sendMessage() {
        const connId = document.getElementById('send-conn-id')?.value;
        const from = document.getElementById('send-from')?.value;
        const to = document.getElementById('send-to')?.value;
        const text = document.getElementById('send-text')?.value;

        if (!connId || !from || !to || !text) {
            showActionResponse('Please fill in all fields (Connection ID, From, To, Text)', 'error');
            return;
        }

        showActionResponse('Sending message...', 'info');

        const formData = new FormData();
        formData.append('from', from);
        formData.append('to', to);
        formData.append('text', text);

        fetch(`/sim/smpp/connection/${connId}/send/message`, {
            method: 'POST',
            body: formData
        })
            .then(response => {
                if (!response.ok) throw new Error('Send failed');
                return response.text();
            })
            .then(result => {
                showActionResponse('Message sent successfully', 'success');
                // Clear form
                document.getElementById('send-from').value = '';
                document.getElementById('send-to').value = '';
                document.getElementById('send-text').value = '';
            })
            .catch(error => {
                console.error('Send error:', error);
                showActionResponse('Failed to send message: ' + error.message, 'error');
            });
    }

    /**
     * Send delivery receipt
     */
    function sendDeliveryReceipt() {
        const connId = document.getElementById('dr-conn-id')?.value;
        const messageId = document.getElementById('dr-message-id')?.value;
        const status = document.getElementById('dr-status')?.value;

        if (!connId || !messageId || !status) {
            showActionResponse('Please fill in all DR fields (Connection ID, Message ID, Status)', 'error');
            return;
        }

        showActionResponse('Sending delivery receipt...', 'info');

        const formData = new FormData();
        formData.append('messageId', messageId);
        formData.append('status', status);

        fetch(`/sim/smpp/connection/${connId}/send/dr`, {
            method: 'POST',
            body: formData
        })
            .then(response => {
                if (!response.ok) throw new Error('Send DR failed');
                return response.text();
            })
            .then(result => {
                showActionResponse('Delivery receipt sent successfully', 'success');
                // Clear form
                document.getElementById('dr-message-id').value = '';
            })
            .catch(error => {
                console.error('Send DR error:', error);
                showActionResponse('Failed to send DR: ' + error.message, 'error');
            });
    }

    /**
     * Group concat messages
     */
    function groupConcatMessages() {
        // Check if table-grouping.js is available
        if (typeof window.TableGrouping === 'undefined') {
            showActionResponse('Table grouping module not loaded', 'error');
            return;
        }

        showActionResponse('Fetching grouped messages...', 'info');

        fetch('/sim/messages/grouped-by-concat')
            .then(response => {
                if (!response.ok) throw new Error('Group concat failed');
                return response.json();
            })
            .then(data => {
                state.messages = data;
                state.filteredMessages = [...data];
                state.currentPage = 1;
                renderTable();
                updateResultsInfo();
                showActionResponse(`Grouped into ${data.length} concat groups`, 'success');
            })
            .catch(error => {
                console.error('Group concat error:', error);
                showActionResponse('Failed to group messages: ' + error.message, 'error');
            });
    }

    /**
     * Setup export functionality
     */
    function setupExport() {
        const exportBtn = document.getElementById('export-btn');
        if (!exportBtn) return;

        exportBtn.addEventListener('click', () => {
            // Check if table-export.js is available
            if (typeof window.TableExport === 'undefined') {
                showActionResponse('Table export module not loaded', 'error');
                return;
            }

            // Show export options dialog
            const format = prompt('Export format: CSV or JSON?', 'CSV');
            if (!format) return;

            if (format.toUpperCase() === 'CSV') {
                exportToCSV();
            } else if (format.toUpperCase() === 'JSON') {
                exportToJSON();
            } else {
                showActionResponse('Invalid format. Use CSV or JSON', 'error');
            }
        });
    }

    /**
     * Export table to CSV
     */
    function exportToCSV() {
        const headers = ['ID', 'Time', 'Direction', 'From', 'To', 'Text', 'Encoding', 'Provider'];
        const rows = state.filteredMessages.map(msg => [
            msg.id || '',
            msg.messageTime || '',
            msg.dir || '',
            msg.from || '',
            msg.to || '',
            msg.text || '',
            msg.messageEncoding || '',
            msg.providerId || ''
        ]);

        let csv = headers.join(',') + '\n';
        rows.forEach(row => {
            csv += row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(',') + '\n';
        });

        downloadFile(csv, 'messages.csv', 'text/csv');
        showActionResponse('Exported to CSV', 'success');
    }

    /**
     * Export table to JSON
     */
    function exportToJSON() {
        const json = JSON.stringify(state.filteredMessages, null, 2);
        downloadFile(json, 'messages.json', 'application/json');
        showActionResponse('Exported to JSON', 'success');
    }

    /**
     * Download file helper
     */
    function downloadFile(content, filename, mimeType) {
        const blob = new Blob([content], { type: mimeType });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }

    /**
     * Render table with current page of filtered messages
     */
    function renderTable() {
        const tbody = document.getElementById('table-body');
        if (!tbody) return;

        // Calculate pagination
        const start = (state.currentPage - 1) * state.pageSize;
        const end = start + state.pageSize;
        const pageMessages = state.filteredMessages.slice(start, end);

        // Clear existing rows
        tbody.innerHTML = '';

        // Render rows
        pageMessages.forEach(msg => {
            const row = createMessageRow(msg);
            tbody.appendChild(row);
        });

        // Update pagination button states
        updatePaginationButtons();
    }

    /**
     * Create table row for message
     */
    function createMessageRow(msg) {
        const row = document.createElement('tr');
        row.dataset.id = msg.id || '';

        // ID
        const idCell = document.createElement('td');
        idCell.textContent = msg.id || '-';
        row.appendChild(idCell);

        // Time
        const timeCell = document.createElement('td');
        timeCell.textContent = msg.messageTime || '-';
        row.appendChild(timeCell);

        // Direction
        const dirCell = document.createElement('td');
        const dirBadge = document.createElement('span');
        dirBadge.className = `dir-badge ${msg.dir === 'In' ? 'dir-in' : 'dir-out'}`;
        dirBadge.textContent = msg.dir || '-';
        dirCell.appendChild(dirBadge);
        row.appendChild(dirCell);

        // From
        const fromCell = document.createElement('td');
        fromCell.textContent = msg.from || '-';
        row.appendChild(fromCell);

        // To
        const toCell = document.createElement('td');
        toCell.textContent = msg.to || '-';
        row.appendChild(toCell);

        // Text (truncated)
        const textCell = document.createElement('td');
        textCell.className = 'truncate';
        textCell.textContent = msg.text || '-';
        textCell.title = msg.text || '';
        row.appendChild(textCell);

        // Encoding
        const encodingCell = document.createElement('td');
        encodingCell.textContent = msg.messageEncoding || '-';
        row.appendChild(encodingCell);

        // Provider
        const providerCell = document.createElement('td');
        providerCell.textContent = msg.providerId || '-';
        row.appendChild(providerCell);

        // Actions
        const actionsCell = document.createElement('td');
        const viewBtn = document.createElement('button');
        viewBtn.className = 'btn btn-sm btn-secondary view-details-btn';
        viewBtn.innerHTML = '<i class="fas fa-eye"></i>';
        viewBtn.title = 'View Details';
        viewBtn.addEventListener('click', () => {
            showMessageDetails(msg);
        });
        actionsCell.appendChild(viewBtn);
        row.appendChild(actionsCell);

        return row;
    }

    /**
     * Show message details modal
     */
    function showMessageDetails(msg) {
        // TODO: Implement modal dialog for message details
        // This will be implemented in Phase 4
        console.log('View details for message:', msg);
        alert(`Message Details:\n\nID: ${msg.id}\nFrom: ${msg.from}\nTo: ${msg.to}\nText: ${msg.text}\nEncoding: ${msg.messageEncoding}\nProvider: ${msg.providerId}`);
    }

    /**
     * Update pagination button states
     */
    function updatePaginationButtons() {
        const totalPages = Math.ceil(state.filteredMessages.length / state.pageSize);

        const firstPageBtn = document.getElementById('first-page');
        const prevPageBtn = document.getElementById('prev-page');
        const nextPageBtn = document.getElementById('next-page');
        const lastPageBtn = document.getElementById('last-page');

        if (firstPageBtn) firstPageBtn.disabled = state.currentPage === 1;
        if (prevPageBtn) prevPageBtn.disabled = state.currentPage === 1;
        if (nextPageBtn) nextPageBtn.disabled = state.currentPage >= totalPages;
        if (lastPageBtn) lastPageBtn.disabled = state.currentPage >= totalPages;
    }

    /**
     * Update results info display
     */
    function updateResultsInfo() {
        const visibleCount = document.getElementById('visible-count');
        const totalCount = document.getElementById('total-count');
        const currentPageSpan = document.getElementById('current-page');
        const totalPagesSpan = document.getElementById('total-pages');

        if (visibleCount) visibleCount.textContent = state.filteredMessages.length;
        if (totalCount) totalCount.textContent = state.messages.length;

        const totalPages = Math.ceil(state.filteredMessages.length / state.pageSize) || 1;
        if (currentPageSpan) currentPageSpan.textContent = state.currentPage;
        if (totalPagesSpan) totalPagesSpan.textContent = totalPages;
    }

    /**
     * Show action response message in popup
     */
    function showActionResponse(message, type) {
        const popup = document.getElementById('action-response-popup');
        const overlay = document.getElementById('action-response-overlay');
        const textDiv = document.getElementById('action-response-text');

        if (!popup || !overlay || !textDiv) {
            console.log(`[${type}] ${message}`);
            return;
        }

        // Set message and type
        textDiv.textContent = message;
        textDiv.className = `action-response-text ${type}`;

        // Show popup and overlay
        popup.classList.add('show');
        overlay.classList.add('show');
    }

    /**
     * Close action response popup
     */
    window.closeActionPopup = function() {
        const popup = document.getElementById('action-response-popup');
        const overlay = document.getElementById('action-response-overlay');

        if (popup) popup.classList.remove('show');
        if (overlay) overlay.classList.remove('show');
    };

    /**
     * Setup popup functionality
     */
    function setupPopup() {
        // Close popup when clicking overlay
        const overlay = document.getElementById('action-response-overlay');
        if (overlay) {
            overlay.addEventListener('click', window.closeActionPopup);
        }

        // Copy button functionality
        const copyBtn = document.getElementById('action-response-copy');
        if (copyBtn) {
            copyBtn.addEventListener('click', () => {
                const textDiv = document.getElementById('action-response-text');
                if (textDiv) {
                    const text = textDiv.textContent;
                    navigator.clipboard.writeText(text).then(() => {
                        const originalHTML = copyBtn.innerHTML;
                        copyBtn.innerHTML = '<i class="fas fa-check"></i> Copied!';
                        setTimeout(() => {
                            copyBtn.innerHTML = originalHTML;
                        }, 2000);
                    }).catch(err => {
                        console.error('Failed to copy:', err);
                    });
                }
            });
        }

        // Close popup with Escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                window.closeActionPopup();
            }
        });
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            init();
            setupPopup();
        });
    } else {
        init();
        setupPopup();
    }

})();
