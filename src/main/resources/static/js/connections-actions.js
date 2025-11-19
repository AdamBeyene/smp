/**
 * SMPP Simulator - Connection Actions JavaScript
 * Handles connection action forms, API calls, and responses
 */

document.addEventListener('DOMContentLoaded', function() {
    // Add connection actions to the sidebar menu
    addConnectionActionsToSidebar();

    // Setup action handlers
    setupActionHandlers();

    // Set up search functionality
    setupSearch();

    // --- Add refresh controls to unified-controls-container ---
    const controls = document.querySelector('.unified-controls-container');
    const table = document.querySelector('.unified-table');
    if (controls && table && window.APP && window.APP.addRefreshControls) {
        window.APP.addRefreshControls(controls, table, fetchConnectionsTableData);
    }
});

/**
 * Add connection actions to the sidebar
 */
function addConnectionActionsToSidebar() {
    // Find the sidebar to add our action links
    const sidebar = document.querySelector('.sidebar');
    if (!sidebar) return;

    // Create action links section
    const actionsSection = document.createElement('div');
    actionsSection.className = 'sidebar-section';
    actionsSection.innerHTML = `
        <div class="sidebar-heading collapsible">Connection Actions</div>
        <div class="sidebar-group">
            <!-- Add connection specific actions here if needed -->
        </div>
        
        <div class="sidebar-heading collapsible">SMPP Actions</div>
        <div class="sidebar-group">
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

    // If sidebar content already exists, just add our sections
    const existingContent = sidebar.querySelector('p');
    if (existingContent) {
        sidebar.appendChild(actionsSection);
    } else {
        sidebar.innerHTML = '';
        sidebar.appendChild(actionsSection);
    }

    // Setup collapsible sections
    setupCollapsibleSections();
}

/**
 * Setup collapsible sections in the sidebar
 */
function setupCollapsibleSections() {
    document.querySelectorAll('.sidebar-heading.collapsible').forEach(heading => {
        // Make sections expanded by default for Connection Actions, collapsed for others
        const isConnectionActions = heading.textContent.trim() === 'Connection Actions';
        const group = heading.nextElementSibling;

        if (group && group.classList.contains('sidebar-group')) {
            if (!isConnectionActions) {
                group.style.display = 'none';
            }

            // Add arrow indicator if not already present
            if (!heading.querySelector('.arrow')) {
                const arrow = document.createElement('span');
                arrow.className = 'arrow' + (isConnectionActions ? ' down' : '');
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
function handleSectionToggle() {
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
 * Setup search functionality
 */
function setupSearch() {
    const searchInput = document.getElementById('search-input');
    if (!searchInput) return;

    searchInput.addEventListener('input', function() {
        const query = this.value.toLowerCase().trim();

        // Get all connection rows - adjust selector to match your table structure
        const rows = document.querySelectorAll('table tbody tr');

        rows.forEach(row => {
            let match = false;
            // Check all cells in the row for matching text
            row.querySelectorAll('td').forEach(cell => {
                if (cell.textContent.toLowerCase().includes(query)) {
                    match = true;
                }
            });

            // Show/hide row based on match
            row.style.display = match || query === '' ? '' : 'none';
        });
    });
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

    // Setup form action submissions
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
    const actionsSection = document.getElementById('connection-actions-section');
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
        const section = document.getElementById('connection-actions-section');
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

    switch (action) {
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
        headers: {
            'Accept': 'application/json, text/plain'
        },
        body: data
    })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            return response.text();
        })
        .then(data => {
            showResponse(data);
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

// --- Fetch connections table data (AJAX) ---
function fetchConnectionsTableData() {
    // Example: reload table body via AJAX (adjust endpoint as needed)
    return fetch('/sim/getConnectionsTableHtml')
        .then(r => {
            if (!r.ok) throw new Error('Failed to fetch table data');
            return r.text();
        })
        .then(html => {
            const table = document.querySelector('.unified-table');
            if (!table) return;
            // Parse only the <tbody> from the returned HTML
            let temp = document.createElement('div');
            temp.innerHTML = html;
            let newTbody = temp.querySelector('tbody');
            if (!newTbody) {
                // fallback: treat all as tbody if only rows returned
                temp = document.createElement('tbody');
                temp.innerHTML = html;
                newTbody = temp;
            }
            const oldTbody = table.querySelector('tbody');
            if (oldTbody && newTbody) {
                oldTbody.parentNode.replaceChild(newTbody, oldTbody);
            }
        });
}
