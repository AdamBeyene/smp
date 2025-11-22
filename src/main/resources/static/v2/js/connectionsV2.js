/**
 * Connections V2 Page - JavaScript Functionality
 * Provides table functionality, search, sorting, and connection actions
 */

(function() {
    'use strict';

    // Global state
    const state = {
        connections: window.connectionsData || [],
        filteredConnections: [],
        sortColumn: null,
        sortDirection: 'asc',
        autoRefreshInterval: null,
        searchTerm: '',
        groupedMode: false
    };

    /**
     * Initialize page functionality when DOM is ready
     */
    function init() {
        console.log('🚀 Initializing Connections V2 page...');

        // Initialize state from data
        state.filteredConnections = [...state.connections];

        // Setup event handlers
        setupSearch();
        setupSorting();
        setupRefresh();
        setupConnectionActions();
        setupGrouping();
        setupExport();

        // Initial render
        renderTable();
        updateResultsInfo();

        console.log(`✅ Connections V2 initialized with ${state.connections.length} connections`);
    }

    /**
     * Setup search functionality
     */
    function setupSearch() {
        const searchInput = document.getElementById('search-input');
        if (!searchInput) return;

        searchInput.addEventListener('input', (e) => {
            state.searchTerm = e.target.value.toLowerCase().trim();
            filterConnections();
            renderTable();
            updateResultsInfo();
        });
    }

    /**
     * Filter connections based on search term
     */
    function filterConnections() {
        if (!state.searchTerm) {
            state.filteredConnections = [...state.connections];
            return;
        }

        state.filteredConnections = state.connections.filter(conn => {
            return (
                (conn.id && String(conn.id).toLowerCase().includes(state.searchTerm)) ||
                (conn.name && conn.name.toLowerCase().includes(state.searchTerm)) ||
                (conn.type && conn.type.toLowerCase().includes(state.searchTerm)) ||
                (conn.transmitterState && conn.transmitterState.toLowerCase().includes(state.searchTerm)) ||
                (conn.receiverState && conn.receiverState.toLowerCase().includes(state.searchTerm))
            );
        });
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
                sortConnections();
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
     * Sort connections based on current sort column and direction
     */
    function sortConnections() {
        if (!state.sortColumn) return;

        state.filteredConnections.sort((a, b) => {
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
                refreshConnections();
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
                        refreshConnections();
                    }, interval);
                    console.log(`🔄 Auto-refresh enabled: ${interval / 1000}s`);
                }
            });
        }
    }

    /**
     * Refresh connections from server
     */
    function refreshConnections() {
        console.log('🔄 Refreshing connections...');
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
     * Setup connection action buttons
     */
    function setupConnectionActions() {
        // Get Connection Info
        const getInfoBtn = document.getElementById('get-info-btn');
        if (getInfoBtn) {
            getInfoBtn.addEventListener('click', () => {
                getConnectionInfo();
            });
        }

        // Start Connection
        const startConnBtn = document.getElementById('start-conn-btn');
        if (startConnBtn) {
            startConnBtn.addEventListener('click', () => {
                startConnection();
            });
        }

        // Stop Connection
        const stopConnBtn = document.getElementById('stop-conn-btn');
        if (stopConnBtn) {
            stopConnBtn.addEventListener('click', () => {
                stopConnection();
            });
        }

        // Reset Connection
        const resetConnBtn = document.getElementById('reset-conn-btn');
        if (resetConnBtn) {
            resetConnBtn.addEventListener('click', () => {
                resetConnection();
            });
        }

        // Reset All Connections
        const resetAllBtn = document.getElementById('reset-all-btn');
        if (resetAllBtn) {
            resetAllBtn.addEventListener('click', () => {
                if (confirm('Are you sure you want to reset ALL connections?')) {
                    resetAllConnections();
                }
            });
        }
    }

    /**
     * Get connection info
     */
    function getConnectionInfo() {
        const connId = document.getElementById('info-conn-id')?.value;

        // If no ID provided, get all connections info
        if (!connId || connId.trim() === '') {
            showActionResponse('Showing all connections', 'info');
            return;
        }

        showActionResponse('Getting connection info...', 'info');

        fetch(`/sim/smpp/info/connection/${connId}`)
            .then(response => {
                if (!response.ok) throw new Error('Failed to get connection info');
                return response.json();
            })
            .then(data => {
                showConnectionInfo(data);
            })
            .catch(error => {
                console.error('Get info error:', error);
                showActionResponse('Failed to get connection info: ' + error.message, 'error');
            });
    }

    /**
     * Show connection info in response area
     */
    function showConnectionInfo(info) {
        const responseDiv = document.getElementById('action-response');
        if (!responseDiv) {
            console.log('Connection info:', info);
            return;
        }

        let html = '<div class="connection-info">';
        html += `<strong>Connection ID:</strong> ${info.id || 'N/A'}<br>`;
        html += `<strong>Name:</strong> ${info.name || 'N/A'}<br>`;
        html += `<strong>Type:</strong> ${info.type || 'N/A'}<br>`;
        if (info.transmitter) {
            html += `<strong>Transmitter:</strong> ${info.transmitter.state || 'N/A'} (${info.transmitter.type || 'N/A'})<br>`;
        }
        if (info.receiver) {
            html += `<strong>Receiver:</strong> ${info.receiver.state || 'N/A'} (${info.receiver.type || 'N/A'})<br>`;
        }
        html += '</div>';

        responseDiv.innerHTML = html;
        responseDiv.className = 'action-response info';
        responseDiv.style.display = 'block';
    }

    /**
     * Start connection
     */
    function startConnection() {
        const connId = document.getElementById('start-conn-id')?.value;
        const connType = document.getElementById('start-conn-type')?.value || 'both';

        if (!connId) {
            showActionResponse('Please enter a Connection ID', 'error');
            return;
        }

        showActionResponse(`Starting connection ${connId} (${connType})...`, 'info');

        fetch(`/sim/smpp/connection/${connId}/start/${connType}`)
            .then(response => {
                if (!response.ok) throw new Error('Start failed');
                return response.text();
            })
            .then(result => {
                showActionResponse(`Connection ${connId} started successfully (${connType})`, 'success');
                // Refresh after 2 seconds
                setTimeout(() => {
                    refreshConnections();
                }, 2000);
            })
            .catch(error => {
                console.error('Start error:', error);
                showActionResponse('Failed to start connection: ' + error.message, 'error');
            });
    }

    /**
     * Stop connection
     */
    function stopConnection() {
        const connId = document.getElementById('stop-conn-id')?.value;
        const connType = document.getElementById('stop-conn-type')?.value || 'both';

        if (!connId) {
            showActionResponse('Please enter a Connection ID', 'error');
            return;
        }

        showActionResponse(`Stopping connection ${connId} (${connType})...`, 'info');

        fetch(`/sim/smpp/connection/${connId}/stop/${connType}`)
            .then(response => {
                if (!response.ok) throw new Error('Stop failed');
                return response.text();
            })
            .then(result => {
                showActionResponse(`Connection ${connId} stopped successfully (${connType})`, 'success');
                // Refresh after 2 seconds
                setTimeout(() => {
                    refreshConnections();
                }, 2000);
            })
            .catch(error => {
                console.error('Stop error:', error);
                showActionResponse('Failed to stop connection: ' + error.message, 'error');
            });
    }

    /**
     * Reset connection
     */
    function resetConnection() {
        const connId = document.getElementById('reset-conn-id')?.value;
        const connType = document.getElementById('reset-conn-type')?.value || 'both';

        if (!connId) {
            showActionResponse('Please enter a Connection ID', 'error');
            return;
        }

        showActionResponse(`Resetting connection ${connId} (${connType})...`, 'info');

        fetch(`/sim/smpp/connection/${connId}/reset/${connType}`)
            .then(response => {
                if (!response.ok) throw new Error('Reset failed');
                return response.text();
            })
            .then(result => {
                showActionResponse(`Connection ${connId} reset successfully (${connType})`, 'success');
                // Refresh after 2 seconds
                setTimeout(() => {
                    refreshConnections();
                }, 2000);
            })
            .catch(error => {
                console.error('Reset error:', error);
                showActionResponse('Failed to reset connection: ' + error.message, 'error');
            });
    }

    /**
     * Reset all connections
     */
    function resetAllConnections() {
        showActionResponse('Resetting all connections...', 'info');

        fetch('/sim/smpp/connections/reset/all')
            .then(response => {
                if (!response.ok) throw new Error('Reset all failed');
                return response.text();
            })
            .then(result => {
                showActionResponse('All connections reset successfully', 'success');
                // Refresh after 2 seconds
                setTimeout(() => {
                    refreshConnections();
                }, 2000);
            })
            .catch(error => {
                console.error('Reset all error:', error);
                showActionResponse('Failed to reset all connections: ' + error.message, 'error');
            });
    }

    /**
     * Setup grouping functionality
     */
    function setupGrouping() {
        const groupFamiliesBtn = document.getElementById('group-families-btn');
        const expandAllBtn = document.getElementById('expand-all-btn');
        const collapseAllBtn = document.getElementById('collapse-all-btn');
        const groupControls = document.getElementById('group-controls');

        if (groupFamiliesBtn) {
            groupFamiliesBtn.addEventListener('click', () => {
                state.groupedMode = !state.groupedMode;

                if (state.groupedMode) {
                    groupFamiliesBtn.innerHTML = '<i class="fas fa-list"></i> Ungroup Families';
                    if (groupControls) groupControls.style.display = 'block';
                    fetchGroupedConnections();
                } else {
                    groupFamiliesBtn.innerHTML = '<i class="fas fa-layer-group"></i> Group Families';
                    if (groupControls) groupControls.style.display = 'none';
                    state.filteredConnections = [...state.connections];
                    renderTable();
                }
            });
        }

        if (expandAllBtn) {
            expandAllBtn.addEventListener('click', () => {
                document.querySelectorAll('.family-group.collapsed').forEach(group => {
                    group.classList.remove('collapsed');
                });
            });
        }

        if (collapseAllBtn) {
            collapseAllBtn.addEventListener('click', () => {
                document.querySelectorAll('.family-group').forEach(group => {
                    group.classList.add('collapsed');
                });
            });
        }
    }

    /**
     * Fetch grouped connections from server
     */
    function fetchGroupedConnections() {
        showActionResponse('Grouping connections by family...', 'info');

        fetch('/api/v2/connections/grouped')
            .then(response => {
                if (!response.ok) throw new Error('Grouping failed');
                return response.json();
            })
            .then(data => {
                renderGroupedTable(data.grouped);
                showActionResponse(`Grouped into ${data.totalFamilies} families`, 'success');
            })
            .catch(error => {
                console.error('Grouping error:', error);
                showActionResponse('Failed to group connections: ' + error.message, 'error');
                state.groupedMode = false;
            });
    }

    /**
     * Render grouped connections table
     */
    function renderGroupedTable(grouped) {
        const tbody = document.getElementById('table-body');
        if (!tbody) return;

        tbody.innerHTML = '';

        // Render each family group
        Object.keys(grouped).forEach(familyName => {
            const connections = grouped[familyName];

            // Create family header row
            const headerRow = document.createElement('tr');
            headerRow.className = 'family-header';
            headerRow.innerHTML = `
                <td colspan="100%">
                    <i class="fas fa-chevron-down"></i>
                    <strong>${familyName}</strong> (${connections.length} connections)
                </td>
            `;
            headerRow.addEventListener('click', () => {
                const familyGroup = headerRow.nextElementSibling;
                while (familyGroup && familyGroup.classList.contains('family-member')) {
                    familyGroup.style.display = familyGroup.style.display === 'none' ? '' : 'none';
                    familyGroup = familyGroup.nextElementSibling;
                }
                const icon = headerRow.querySelector('i');
                icon.className = icon.className.includes('down') ? 'fas fa-chevron-right' : 'fas fa-chevron-down';
            });
            tbody.appendChild(headerRow);

            // Add connection rows
            connections.forEach(conn => {
                const row = createConnectionRow(conn);
                row.classList.add('family-member');
                tbody.appendChild(row);
            });
        });
    }

    /**
     * Setup export functionality
     */
    function setupExport() {
        const exportBtn = document.getElementById('export-btn');
        if (!exportBtn) return;

        exportBtn.addEventListener('click', () => {
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
        const headers = ['ID', 'Name', 'Type', 'TX State', 'RX State', 'TX Type', 'RX Type', 'TX Port', 'RX Port', 'TX Host', 'RX Host'];
        const rows = state.filteredConnections.map(conn => [
            conn.id || '',
            conn.name || '',
            conn.type || '',
            conn.transmitterState || '',
            conn.receiverState || '',
            conn.transmitterType || '',
            conn.receiverType || '',
            conn.transmitterPort || '',
            conn.receiverPort || '',
            conn.transmitterHost || '',
            conn.receiverHost || ''
        ]);

        let csv = headers.join(',') + '\n';
        rows.forEach(row => {
            csv += row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(',') + '\n';
        });

        downloadFile(csv, 'connections.csv', 'text/csv');
        showActionResponse('Exported to CSV', 'success');
    }

    /**
     * Export table to JSON
     */
    function exportToJSON() {
        const json = JSON.stringify(state.filteredConnections, null, 2);
        downloadFile(json, 'connections.json', 'application/json');
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
     * Render table with filtered connections
     */
    function renderTable() {
        const tbody = document.getElementById('table-body');
        if (!tbody) return;

        // Clear existing rows
        tbody.innerHTML = '';

        // Render rows
        state.filteredConnections.forEach(conn => {
            const row = createConnectionRow(conn);
            tbody.appendChild(row);
        });
    }

    /**
     * Create table row for connection
     */
    function createConnectionRow(conn) {
        const row = document.createElement('tr');
        row.dataset.id = conn.id || '';
        row.dataset.name = conn.name || '';
        row.dataset.type = conn.type || '';

        // ID
        const idCell = document.createElement('td');
        idCell.textContent = conn.id || '-';
        row.appendChild(idCell);

        // Name
        const nameCell = document.createElement('td');
        nameCell.textContent = conn.name || '-';
        row.appendChild(nameCell);

        // Type
        const typeCell = document.createElement('td');
        const typeBadge = document.createElement('span');
        typeBadge.className = `type-badge ${conn.type === 'SMPP' ? 'type-smpp' : 'type-http'}`;
        typeBadge.textContent = conn.type || '-';
        typeCell.appendChild(typeBadge);
        row.appendChild(typeCell);

        // Transmitter State
        const txStateCell = document.createElement('td');
        const txStateBadge = document.createElement('span');
        const isTxActive = conn.transmitterState === 'Bound' || conn.transmitterState === 'Active';
        txStateBadge.className = `state-badge ${isTxActive ? 'state-bound' : 'state-unbound'}`;
        txStateBadge.textContent = conn.transmitterState || '-';
        txStateCell.appendChild(txStateBadge);
        row.appendChild(txStateCell);

        // Receiver State
        const rxStateCell = document.createElement('td');
        const rxStateBadge = document.createElement('span');
        const isRxActive = conn.receiverState === 'Bound' || conn.receiverState === 'Active';
        rxStateBadge.className = `state-badge ${isRxActive ? 'state-bound' : 'state-unbound'}`;
        rxStateBadge.textContent = conn.receiverState || '-';
        rxStateCell.appendChild(rxStateBadge);
        row.appendChild(rxStateCell);

        // Transmitter Type
        const txTypeCell = document.createElement('td');
        txTypeCell.textContent = conn.transmitterType || '-';
        row.appendChild(txTypeCell);

        // Receiver Type
        const rxTypeCell = document.createElement('td');
        rxTypeCell.textContent = conn.receiverType || '-';
        row.appendChild(rxTypeCell);

        // Transmitter Port
        const txPortCell = document.createElement('td');
        txPortCell.textContent = conn.transmitterPort || '-';
        row.appendChild(txPortCell);

        // Receiver Port
        const rxPortCell = document.createElement('td');
        rxPortCell.textContent = conn.receiverPort || '-';
        row.appendChild(rxPortCell);

        // Transmitter Host
        const txHostCell = document.createElement('td');
        txHostCell.className = 'truncate';
        txHostCell.textContent = conn.transmitterHost || '-';
        txHostCell.title = conn.transmitterHost || '';
        row.appendChild(txHostCell);

        // Receiver Host
        const rxHostCell = document.createElement('td');
        rxHostCell.className = 'truncate';
        rxHostCell.textContent = conn.receiverHost || '-';
        rxHostCell.title = conn.receiverHost || '';
        row.appendChild(rxHostCell);

        // Cloudhopper-specific columns (if enabled)
        if (window.isCloudhopper) {
            // Enquire Link
            const elCell = document.createElement('td');
            const elIndicator = document.createElement('span');
            elIndicator.className = `status-indicator ${conn.enquireLinkStatus === 'OK' ? 'status-ok' : 'status-error'}`;
            elIndicator.textContent = conn.enquireLinkStatus || '-';
            elCell.appendChild(elIndicator);
            row.appendChild(elCell);

            // Reconnects
            const reconnectCell = document.createElement('td');
            reconnectCell.textContent = conn.reconnectCount || '0';
            row.appendChild(reconnectCell);

            // Throughput
            const throughputCell = document.createElement('td');
            throughputCell.textContent = (conn.throughput || '0') + '/s';
            row.appendChild(throughputCell);
        }

        return row;
    }

    /**
     * Update results info display
     */
    function updateResultsInfo() {
        const visibleCount = document.getElementById('visible-count');
        const totalCount = document.getElementById('total-count');

        if (visibleCount) visibleCount.textContent = state.filteredConnections.length;
        if (totalCount) totalCount.textContent = state.connections.length;
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
