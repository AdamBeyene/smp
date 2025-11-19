/**
 * SMPP Simulator - Message Actions JavaScript
 * Handles message action forms, API calls, and responses
 */

document.addEventListener('DOMContentLoaded', function() {
    // If we're on the messages page (check for required elements)
    if (document.getElementById('message-actions-section') || document.querySelector('.sidebar')) {
        console.log('Messages actions module loaded');

        // Initialize sidebar actions if needed
        initializeSidebar();

        // Set up event handlers for action forms
        setupActionHandlers();

        // Set up collapsible sections
        setupCollapsibleSections();

        // Apply custom table styles
        applyCustomTableStyles();
    }

    // Find original tooltip handler and enhance it
    document.addEventListener('click', function(e) {
        if (e.target.classList.contains('truncated-cell')) {
            const fullText = e.target.parentElement.__fullText || e.target.textContent;
            const tooltip = createTooltip(fullText, e.target);

            // Position and show tooltip
            // ... existing positioning code ...
        }
    });

    // --- Add refresh controls to unified-controls-container ---
    const controls = document.querySelector('.unified-controls-container');
    const table = document.querySelector('.unified-table');

    // --- Add filter submenu logic ---
    if (controls) {
        addFilterSubmenu(controls, table);
    }

    if (controls && table && window.APP && window.APP.addRefreshControls) {
        window.APP.addRefreshControls(controls, table, fetchConnectionsTableData);
    }

});

/**
 * Apply custom styles to the messages table
 */
function applyCustomTableStyles() {
    // Find or assign the target table
    let table = document.querySelector('.messages-table');
    if (!table) {
        table = document.querySelector('table');
        if (!table) {
            console.warn('No table found for styling');
            return;
        }
        table.classList.add('messages-table');
    }

    // Get header cells, try thead first, fallback to first row
    let headers = table.querySelectorAll('thead th');
    if (headers.length === 0) {
        headers = table.querySelectorAll('tr:first-of-type th');
    }
    if (!headers.length) {
        console.warn('No header cells found; styles not applied');
        return;
    }

    let dirIndex = -1, timeIndex = -1;
    headers.forEach((th, i) => {
        const text = th.textContent.trim().toLowerCase();
        if (text.includes('dir')) dirIndex = i + 1;
        if (text.includes('time')) timeIndex = i + 1;
    });

    let css = `
        /* Enlarge headers & add padding */
        .messages-table th { font-size:1.1em !important; padding:12px 10px !important; }
        .messages-table td { padding:10px !important; vertical-align:top !important; }
    `;
    if (dirIndex > 0) {
        css += `
        /* Force 'Dir' column narrow */
        .messages-table th:nth-child(${dirIndex}),
        .messages-table td:nth-child(${dirIndex}) {
            width:60px !important;
            min-width:60px !important;
            max-width:60px !important;
            overflow:hidden !important;
            text-overflow:ellipsis !important;
            white-space:nowrap !important;
        }
        `;
    }
    if (timeIndex > 0) {
        css += `
        /* Force 'Message Time' column wide */
        .messages-table th:nth-child(${timeIndex}),
        .messages-table td:nth-child(${timeIndex}) {
            min-width:190px !important;
            max-width:none !important;
            overflow:visible !important;
            white-space:nowrap !important;
        }
        `;
    }

    const style = document.createElement('style');
    style.textContent = css;
    document.head.appendChild(style);
    console.log('Custom table styles applied');
}

/**
 * Initialize the sidebar with action sections if they don't exist
 */
function initializeSidebar() {
    // Check if sidebar actions have been added
    if (!document.querySelector('.sidebar-section')) {
        addMessageActionsToSidebar();
    }
}

/**
 * Setup collapsible sections in the sidebar
 */
function setupCollapsibleSections() {
    document.querySelectorAll('.sidebar-heading.collapsible').forEach(heading => {
        // Make sections collapsed by default except Message Actions
        const isMessageActions = heading.textContent.trim() === 'Message Actions';
        const group = heading.nextElementSibling;

        if (group && group.classList.contains('sidebar-group')) {
            // Initial state - collapsed except Message Actions
            if (!isMessageActions) {
                group.style.display = 'none';
            }

            // Add arrow indicator if not present
            if (!heading.querySelector('.arrow')) {
                const arrow = document.createElement('span');
                arrow.className = 'arrow' + (isMessageActions ? ' down' : '');
                heading.appendChild(arrow);
            }
        }

        // Remove existing click handler to prevent duplicates
        heading.removeEventListener('click', handleSectionToggle);

        // Add click handler
        heading.addEventListener('click', handleSectionToggle);
    });
}

/**
 * Handle clicking on collapsible section headers
 */
function handleSectionToggle(event) {
    const heading = this;
    const group = heading.nextElementSibling;

    if (group && group.classList.contains('sidebar-group')) {
        const isVisible = group.style.display !== 'none';
        group.style.display = isVisible ? 'none' : 'block';

        // Toggle arrow icon
        const arrow = heading.querySelector('.arrow');
        if (arrow) {
            arrow.classList.toggle('down');
        }
    }
}

/**
 * Setup action handlers for all form submissions
 */
function setupActionHandlers() {
    // Handle clicks on sidebar action links
    document.addEventListener('click', function(e) {
        const actionItem = e.target.closest('.sidebar-item[data-action]');
        if (actionItem) {
            const action = actionItem.dataset.action;
            showActionForm(action);
            e.preventDefault();
        }
    });

    // Handle close button
    const closeBtn = document.getElementById('close-action-btn');
    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            hideAllActionForms();
        });
    }

    // Setup message action submissions
    setupSubmitHandler('delete-all-submit', 'delete-all');
    setupSubmitHandler('search-text-submit', 'search-by-text');
    setupSubmitHandler('search-mid-submit', 'search-by-mid');

    // Setup SimSMPP action submissions
    setupSubmitHandler('send-message-submit', 'send-message');
    setupSubmitHandler('send-dr-submit', 'send-dr');
    setupSubmitHandler('connection-info-submit', 'connection-info');
    setupSubmitHandler('start-connection-submit', 'start-connection');
    setupSubmitHandler('stop-connection-submit', 'stop-connection');
    setupSubmitHandler('reset-connection-submit', 'reset-connection');
    setupSubmitHandler('reset-all-submit', 'reset-all-connections');
}

/**
 * Helper to set up a submit handler for an action button
 */
function setupSubmitHandler(buttonId, action) {
    const button = document.getElementById(buttonId);
    if (button) {
        button.addEventListener('click', function() {
            executeAction(action);
        });
    }
}

/**
 * Show an action form based on the action type
 */
function showActionForm(action) {
    // Hide all forms first
    hideAllActionForms(false);

    // Show the actions section
    const actionsSection = document.getElementById('message-actions-section');
    if (actionsSection) {
        actionsSection.style.display = 'block';
    }

    // Clear previous response
    const responseContent = document.getElementById('response-content');
    const responseArea = document.getElementById('action-response-area');
    if (responseContent && responseArea) {
        responseContent.textContent = '';
        responseArea.style.display = 'none';
    }

    // Show the specific form and set title
    let title = '';
    let formId = '';

    switch (action) {
        case 'delete-all':
            formId = 'delete-all-form';
            title = 'Delete All Messages';
            break;
        case 'search-by-text':
            formId = 'search-by-text-form';
            title = 'Search Messages by Text';
            break;
        case 'search-by-mid':
            formId = 'search-by-mid-form';
            title = 'Search Messages by ID';
            break;
        case 'send-message':
            formId = 'send-message-form';
            title = 'Send SMPP Message';
            break;
        case 'send-dr':
            formId = 'send-dr-form';
            title = 'Send Delivery Receipt';
            break;
        case 'connection-info':
            formId = 'connection-info-form';
            title = 'Connection Information';
            break;
        case 'start-connection':
            formId = 'start-connection-form';
            title = 'Start Connection';
            break;
        case 'stop-connection':
            formId = 'stop-connection-form';
            title = 'Stop Connection';
            break;
        case 'reset-connection':
            formId = 'reset-connection-form';
            title = 'Reset Connection';
            break;
        case 'reset-all':
            formId = 'reset-all-form';
            title = 'Reset All Connections';
            break;
    }

    // Show form and set title
    if (formId) {
        const form = document.getElementById(formId);
        if (form) {
            form.style.display = 'block';
        }
    }

    const titleElement = document.getElementById('current-action-title');
    if (titleElement) {
        titleElement.textContent = title;
    }
}

/**
 * Hide all action forms
 */
function hideAllActionForms(hideSection = true) {
    // Hide all forms
    document.querySelectorAll('.action-form').forEach(form => {
        form.style.display = 'none';
    });

    // Hide the section if requested
    if (hideSection) {
        const section = document.getElementById('message-actions-section');
        if (section) {
            section.style.display = 'none';
        }
    }
}

/**
 * Execute an action based on the action type
 */
function executeAction(action) {
    let url = '';
    let method = 'GET';
    let data = null;
    let contentType = 'application/json';

    switch (action) {
        case 'delete-all':
            url = '/sim/deleteAllMessages';
            method = 'DELETE';
            break;
        case 'search-by-text':
            const searchText = document.getElementById('search-text-input').value.trim();
            if (!searchText) {
                showResponse('Please enter text to search for', 'error');
                return;
            }
            url = `/sim/getMessagesByTextContains?text=${encodeURIComponent(searchText)}`;
            break;
        case 'search-by-mid':
            const searchMid = document.getElementById('search-mid-input').value.trim();
            if (!searchMid) {
                showResponse('Please enter a message ID', 'error');
                return;
            }
            url = `/sim/getMessagesByMid?mid=${encodeURIComponent(searchMid)}`;
            break;

        // SimSMPP Actions
        case 'send-message':
            const connId = document.getElementById('send-connection-id').value.trim();
            if (!connId) {
                showResponse('Connection ID is required', 'error');
                return;
            }

            url = `/sim/smpp/connection/${connId}/send/message`;
            method = 'POST';

            data = JSON.stringify({
                src: document.getElementById('send-source-addr').value.trim(),
                dst: document.getElementById('send-dest-addr').value.trim(),
                serviceType: document.getElementById('send-service-type').value.trim() || null,
                text: document.getElementById('send-message-text').value.trim()
            });
            break;

        case 'send-dr':
            const drConnId = document.getElementById('dr-connection-id').value.trim();
            if (!drConnId) {
                showResponse('Connection ID is required', 'error');
                return;
            }

            url = `/sim/smpp/connection/${drConnId}/send/dr`;
            method = 'POST';

            data = JSON.stringify({
                src: document.getElementById('dr-source-addr').value.trim(),
                dst: document.getElementById('dr-dest-addr').value.trim(),
                serviceType: document.getElementById('dr-service-type').value.trim() || null,
                status: document.getElementById('dr-status').value,
                providerResult: document.getElementById('dr-provider-result').value.trim() || null
            });
            break;

        case 'connection-info':
            const infoId = document.getElementById('conn-info-id').value.trim();
            url = infoId ? `/sim/smpp/info/connection/${infoId}` : '/sim/smpp/info/connection';
            break;

        case 'start-connection':
            const startId = document.getElementById('start-connection-id').value.trim();
            const startType = document.getElementById('start-connection-type').value;

            if (!startId) {
                showResponse('Connection ID is required', 'error');
                return;
            }

            url = `/sim/smpp/connection/${startId}/start/${startType}`;
            break;

        case 'stop-connection':
            const stopId = document.getElementById('stop-connection-id').value.trim();
            const stopType = document.getElementById('stop-connection-type').value;

            if (!stopId) {
                showResponse('Connection ID is required', 'error');
                return;
            }

            url = `/sim/smpp/connection/${stopId}/stop/${stopType}`;
            break;

        case 'reset-connection':
            const resetId = document.getElementById('reset-connection-id').value.trim();
            const resetType = document.getElementById('reset-connection-type').value;

            if (!resetId) {
                showResponse('Connection ID is required', 'error');
                return;
            }

            url = `/sim/smpp/connection/${resetId}/reset/${resetType}`;
            break;

        case 'reset-all-connections':
            url = '/sim/smpp/connections/reset/all';
            break;
    }

    // Show loading state
    showResponse('Loading...', 'loading');

    // Make the request
    fetch(url, {
        method: method,
        headers: contentType && data ? {
            'Content-Type': contentType,
            'Accept': 'application/json'
        } : {
            'Accept': 'application/json'
        },
        body: data
    })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            // For JSON responses
            if (response.headers.get('content-type')?.includes('application/json')) {
                return response.json().then(data => {
                    return { isJson: true, data };
                });
            }

            // For text responses
            return response.text().then(text => {
                return { isJson: false, data: text };
            });
        })
        .then(({ isJson, data }) => {
            // Format and display the response
            const formattedResponse = isJson ? JSON.stringify(data, null, 2) : data;
            showResponse(formattedResponse);

            // Handle specific action responses
            if (action === 'delete-all' && (data === true || data === 'true')) {
                showResponse('All messages have been successfully deleted.');
                setTimeout(() => {
                    window.location.reload();
                }, 2000);
            }
            else if (action === 'reset-all-connections') {
                showResponse(`Reset command executed successfully.\n\n${formattedResponse}`);
            }
            else if (action.includes('connection')) {
                showResponse(`Connection action completed.\n\n${formattedResponse}`);
            }
        })
        .catch(error => {
            console.error('Error executing action:', error);
            showResponse(`Error: ${error.message}`, 'error');
        });
}

/**
 * Show a response message
 */
function showResponse(message, type = 'success') {
    const responseArea = document.getElementById('action-response-area');
    const responseContent = document.getElementById('response-content');

    if (!responseArea || !responseContent) return;

    responseArea.style.display = 'block';
    responseContent.textContent = message;

    // Apply styling based on response type
    responseArea.className = 'action-response-area';
    if (type === 'error') {
        responseArea.classList.add('error');
    } else if (type === 'loading') {
        responseArea.classList.add('loading');
    } else {
        responseArea.classList.add('success');
    }
}

/**
 * Add message actions to the sidebar
 */
function addMessageActionsToSidebar() {
    // Find the sidebar to add our action links
    const sidebar = document.querySelector('.sidebar');
    if (!sidebar) return;

    // Create action links section
    const actionsSection = document.createElement('div');
    actionsSection.className = 'sidebar-section';
    actionsSection.innerHTML = `
        <div class="sidebar-heading collapsible">Message Actions</div>
        <div class="sidebar-group">
            <a href="#" class="sidebar-item" data-action="delete-all">
                <i class="fas fa-trash"></i> Delete All Messages
            </a>
            <a href="#" class="sidebar-item" data-action="search-by-text">
                <i class="fas fa-search"></i> Search by Text
            </a>
            <a href="#" class="sidebar-item" data-action="search-by-mid">
                <i class="fas fa-hashtag"></i> Search by Message ID
            </a>
        </div>
        
        <div class="sidebar-heading collapsible">SMPP Actions</div>
        <div class="sidebar-group">
            <a href="#" class="sidebar-item" data-action="send-message">
                <i class="fas fa-paper-plane"></i> Send Message
            </a>
            <a href="#" class="sidebar-item" data-action="send-dr">
                <i class="fas fa-receipt"></i> Send Delivery Receipt
            </a>
            <a href="#" class="sidebar-item" data-action="connection-info">
                <i class="fas fa-info-circle"></i> Connection Info
            </a>
            <a href="#" class="sidebar-item" data-action="start-connection">
                <i class="fas fa-play"></i> Start Connection
            </a>
            <a href="#" class="sidebar-item" data-action="stop-connection">
                <i class="fas fa-stop"></i> Stop Connection
            </a>
            <a href="#" class="sidebar-item" data-action="reset-connection">
                <i class="fas fa-sync"></i> Reset Connection
            </a>
            <a href="#" class="sidebar-item" data-action="reset-all">
                <i class="fas fa-sync-alt"></i> Reset All Connections
            </a>
        </div>
    `;

    // Add after first link group or at the end if not found
    const firstLinkGroup = sidebar.querySelector('.sidebar-heading');
    if (firstLinkGroup && firstLinkGroup.parentNode) {
        // Insert after the first section
        firstLinkGroup.parentNode.insertBefore(actionsSection, firstLinkGroup.parentNode.children[1]);
    } else {
        // Append to the end of sidebar
        sidebar.appendChild(actionsSection);
    }
}

/**
 * Enhanced createTooltip function that adds encoding and binary data info
 */
function createTooltip(text, cellElement) {
    // Create tooltip container
    const tooltip = document.createElement('div');
    tooltip.className = 'custom-tooltip';

    // Add encoding information if available
    const cell = cellElement.closest('span');
    if (cell && cell.hasAttribute('data-encoding')) {
        const encodingInfo = document.createElement('div');
        encodingInfo.className = 'encoding-info';
        encodingInfo.innerHTML = `<strong>Encoding:</strong> ${cell.getAttribute('data-encoding')}`;
        tooltip.appendChild(encodingInfo);
    }

    // Add content
    const contentDiv = document.createElement('div');
    contentDiv.className = 'tooltip-content';
    contentDiv.textContent = text;
    tooltip.appendChild(contentDiv);

    // Add binary data info if applicable
    if (cell && cell.hasAttribute('data-has-binary')) {
        const binaryInfo = document.createElement('div');
        binaryInfo.className = 'binary-data-info';

        // Add download link for binary data
        const messageId = getMessageIdFromCell(cellElement);
        if (messageId) {
            binaryInfo.innerHTML =
                `<strong>Binary Data:</strong> This message contains binary data. ` +
                `<a href="/sim/getMessageRawData/${messageId}" download="message_${messageId}.bin">` +
                `Download Binary Content</a>`;
        } else {
            binaryInfo.innerHTML = "<strong>Binary Data:</strong> This message contains binary data.";
        }

        tooltip.appendChild(binaryInfo);
    }

    return tooltip;
}

/**
 * Helper function to get message ID from a cell
 */
function getMessageIdFromCell(cellElement) {
    // Try to navigate to the row and find the ID cell (assuming ID is first column)
    const row = cellElement.closest('tr');
    if (row) {
        const idCell = row.querySelector('td:first-child');
        return idCell ? idCell.textContent.trim() : null;
    }
    return null;
}

// --- Filter submenu for messages table ---
function addFilterSubmenu(controls, table) {
    // Add Filter button if not present
    if (!controls.querySelector('.unified-filter-btn')) {
        const filterBtn = document.createElement('button');
        filterBtn.className = 'unified-button unified-button-secondary unified-filter-btn';
        filterBtn.innerHTML = '<i class="fas fa-filter"></i> Filter';
        filterBtn.type = 'button';
        controls.insertBefore(filterBtn, controls.firstChild);

        // Filter submenu container
        const submenu = document.createElement('div');
        submenu.className = 'unified-filter-submenu';
        submenu.style.display = 'none';
        submenu.style.position = 'absolute';
        submenu.style.top = '100%';
        submenu.style.left = '0';
        submenu.style.background = 'var(--color-surface)';
        submenu.style.border = '1px solid var(--color-border)';
        submenu.style.borderRadius = '6px';
        submenu.style.boxShadow = '0 2px 8px var(--color-shadow)';
        submenu.style.padding = '12px 16px';
        submenu.style.zIndex = '999';
        submenu.style.minWidth = '260px';

        // Build checkboxes for each column
        const headers = table.querySelectorAll('thead th');
        headers.forEach((th, idx) => {
            const colName = th.textContent.trim();
            const row = document.createElement('div');
            row.style.display = 'flex';
            row.style.alignItems = 'center';
            row.style.gap = '8px';
            const cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.dataset.colIdx = idx;
            cb.id = 'filter-col-' + idx;
            row.appendChild(cb);
            const label = document.createElement('label');
            label.textContent = colName;
            label.htmlFor = cb.id;
            row.appendChild(label);
            // Text input for filter value
            const txt = document.createElement('input');
            txt.type = 'text';
            txt.className = 'unified-input';
            txt.style.display = 'none';
            txt.style.marginLeft = '8px';
            txt.placeholder = 'Value...';
            row.appendChild(txt);

            cb.addEventListener('change', () => {
                txt.style.display = cb.checked ? '' : 'none';
                if (!cb.checked) txt.value = '';
                applyFilters();
            });
            txt.addEventListener('input', applyFilters);
            submenu.appendChild(row);
        });

        // Show/hide submenu
        filterBtn.addEventListener('click', () => {
            submenu.style.display = submenu.style.display === 'none' ? 'block' : 'none';
            // Expand controls container if needed
            controls.style.overflow = 'visible';
        });
        document.body.addEventListener('click', (e) => {
            if (!submenu.contains(e.target) && e.target !== filterBtn) {
                submenu.style.display = 'none';
            }
        });

        controls.appendChild(submenu);

        // Filtering logic
        function applyFilters() {
            const filters = [];
            submenu.querySelectorAll('input[type="checkbox"]').forEach(cb => {
                if (cb.checked) {
                    const idx = parseInt(cb.dataset.colIdx, 10);
                    const val = cb.parentNode.querySelector('input[type="text"]').value.trim().toLowerCase();
                    if (val) filters.push({ idx, val });
                }
            });
            table.querySelectorAll('tbody tr').forEach(tr => {
                let show = true;
                filters.forEach(f => {
                    const cell = tr.children[f.idx];
                    if (!cell || !cell.textContent.toLowerCase().includes(f.val)) show = false;
                });
                tr.style.display = show ? '' : 'none';
            });
            // Save filters for refresh state
            window.APP = window.APP || {};
            window.APP._activeFilters = filters;
        }

        // Expose for refresh state
        window.APP = window.APP || {};
        window.APP.getActiveFilters = () => window.APP._activeFilters || [];
        window.APP.applyFilters = (filters) => {
            submenu.querySelectorAll('input[type="checkbox"]').forEach((cb, i) => {
                const found = filters.find(f => f.idx === i);
                cb.checked = !!found;
                const txt = cb.parentNode.querySelector('input[type="text"]');
                if (txt) {
                    txt.style.display = cb.checked ? '' : 'none';
                    txt.value = found ? found.val : '';
                }
            });
            submenu.style.display = 'none';
            // Actually apply
            submenu.querySelectorAll('input[type="text"]').forEach(txt => {
                txt.dispatchEvent(new Event('input'));
            });
        };
    }
}

// --- Fetch messages table data (AJAX) ---
function fetchMessagesTableData() {
    // Example: reload table body via AJAX (adjust endpoint as needed)
    return fetch('/sim/getMessagesTableHtml')
        .then(r => {
            if (!r.ok) throw new Error('Failed to fetch table data');
            return r.text();
        })
        .then(html => {
            const table = document.querySelector('.unified-table');
            if (!table) return;
            const temp = document.createElement('tbody');
            temp.innerHTML = html;
            const newTbody = temp.querySelector('tbody') || temp;
            const oldTbody = table.querySelector('tbody');
            if (oldTbody && newTbody) {
                oldTbody.parentNode.replaceChild(newTbody, oldTbody);
            }
        });
}
