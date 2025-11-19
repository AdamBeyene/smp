// Unified Table Utilities (for messages/connections pages)

// --- Expandable cell logic for unified tables ---
document.addEventListener('DOMContentLoaded', function () {
    let activeTooltip = null;
    let activeCell = null;
    let activeRowIdx = null;
    let activeTbody = null;

    function createFloatingTooltip(text, cellElement) {
        if (activeTooltip) {
            document.body.removeChild(activeTooltip);
            activeTooltip = null;
        }

        const tooltip = document.createElement('div');
        tooltip.className = 'custom-tooltip enhanced';

        // Get field label from table header
        const cell = cellElement.closest('td');
        let fieldLabel = 'Cell Content';
        if (cell) {
            const cellIndex = Array.from(cell.parentNode.children).indexOf(cell);
            const table = cell.closest('table');
            if (table) {
                const headers = table.querySelectorAll('thead th');
                if (headers[cellIndex]) {
                    fieldLabel = headers[cellIndex].textContent.trim();
                }
            }
        }

        // Header with field label and close button
        const header = document.createElement('div');
        header.className = 'tooltip-header';
        header.style.display = 'flex';
        header.style.justifyContent = 'space-between';
        header.style.alignItems = 'center';
        header.style.marginBottom = '8px';
        header.style.paddingBottom = '6px';
        header.style.borderBottom = '1px solid var(--color-border, #D1D5DB)';

        const label = document.createElement('span');
        label.className = 'tooltip-label';
        label.textContent = fieldLabel;
        label.style.fontWeight = '600';
        label.style.fontSize = '0.95em';
        header.appendChild(label);

        const closeBtn = document.createElement('button');
        closeBtn.className = 'tooltip-close-btn';
        closeBtn.innerHTML = '&times;';
        closeBtn.style.background = 'none';
        closeBtn.style.border = 'none';
        closeBtn.style.fontSize = '1.5em';
        closeBtn.style.cursor = 'pointer';
        closeBtn.style.padding = '0 4px';
        closeBtn.style.lineHeight = '1';
        closeBtn.onclick = () => removeTooltip();
        header.appendChild(closeBtn);

        tooltip.appendChild(header);

        // Check if we need to handle encoding info
        if (cell && cell.title && cell.title.startsWith('Encoding:')) {
            const encodingInfo = document.createElement('div');
            encodingInfo.className = 'encoding-info';
            encodingInfo.textContent = cell.title;
            encodingInfo.style.marginBottom = '6px';
            encodingInfo.style.fontSize = '0.85em';
            encodingInfo.style.color = 'var(--color-text-secondary, #6B7280)';
            tooltip.appendChild(encodingInfo);
        }

        // Set the main text content
        const textContent = document.createElement('div');
        textContent.className = 'tooltip-content';
        textContent.textContent = text;
        textContent.style.marginBottom = '8px';
        textContent.style.whiteSpace = 'pre-wrap';
        textContent.style.wordBreak = 'break-word';
        textContent.style.maxHeight = '50vh';
        textContent.style.overflow = 'auto';
        tooltip.appendChild(textContent);

        // Check for binary data indicators
        if (cell && cell.dataset.hasBinary) {
            const binaryNote = document.createElement('div');
            binaryNote.className = 'binary-note';
            binaryNote.textContent = "⚠ Message contains binary data";
            binaryNote.style.marginBottom = '6px';
            binaryNote.style.fontSize = '0.85em';
            binaryNote.style.color = 'var(--color-warning, #F59E0B)';
            tooltip.appendChild(binaryNote);
        }

        // Action buttons
        const actions = document.createElement('div');
        actions.className = 'tooltip-actions';
        actions.style.display = 'flex';
        actions.style.gap = '8px';
        actions.style.paddingTop = '8px';
        actions.style.borderTop = '1px solid var(--color-border, #D1D5DB)';

        const copyBtn = document.createElement('button');
        copyBtn.className = 'tooltip-action-btn';
        copyBtn.innerHTML = '<i class="fas fa-copy"></i> Copy';
        copyBtn.style.padding = '4px 12px';
        copyBtn.style.border = '1px solid var(--color-border, #D1D5DB)';
        copyBtn.style.borderRadius = '4px';
        copyBtn.style.background = 'var(--color-surface, #FFFFFF)';
        copyBtn.style.cursor = 'pointer';
        copyBtn.style.fontSize = '0.9em';
        copyBtn.onclick = (e) => {
            e.stopPropagation();
            e.preventDefault();

            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(text).then(() => {
                    copyBtn.innerHTML = '<i class="fas fa-check"></i> Copied!';
                    copyBtn.style.background = 'var(--color-success-light, #D1FAE5)';
                    copyBtn.style.color = 'var(--color-success-dark, #065F46)';
                    setTimeout(() => {
                        copyBtn.innerHTML = '<i class="fas fa-copy"></i> Copy';
                        copyBtn.style.background = 'var(--color-surface, #FFFFFF)';
                        copyBtn.style.color = '';
                    }, 2000);
                }).catch(err => {
                    console.error('Failed to copy:', err);
                    // Fallback to old method
                    fallbackCopy(text, copyBtn);
                });
            } else {
                // Fallback for older browsers
                fallbackCopy(text, copyBtn);
            }
        };
        actions.appendChild(copyBtn);

        const selectAllBtn = document.createElement('button');
        selectAllBtn.className = 'tooltip-action-btn';
        selectAllBtn.innerHTML = '<i class="fas fa-i-cursor"></i> Select All';
        selectAllBtn.style.padding = '4px 12px';
        selectAllBtn.style.border = '1px solid var(--color-border, #D1D5DB)';
        selectAllBtn.style.borderRadius = '4px';
        selectAllBtn.style.background = 'var(--color-surface, #FFFFFF)';
        selectAllBtn.style.cursor = 'pointer';
        selectAllBtn.style.fontSize = '0.9em';
        selectAllBtn.onclick = (e) => {
            e.stopPropagation();
            e.preventDefault();

            try {
                const range = document.createRange();
                range.selectNodeContents(textContent);
                const sel = window.getSelection();
                sel.removeAllRanges();
                sel.addRange(range);
            } catch (err) {
                console.error('Error selecting text:', err);
            }
        };
        actions.appendChild(selectAllBtn);

        tooltip.appendChild(actions);

        // Styling
        tooltip.style.position = 'fixed';
        tooltip.style.display = 'block';
        tooltip.style.zIndex = '10000';
        tooltip.style.maxWidth = '600px';
        tooltip.style.minWidth = '300px';
        tooltip.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)';
        tooltip.style.padding = '12px';
        tooltip.style.borderRadius = '6px';
        tooltip.style.backgroundColor = 'var(--color-surface, #FFFFFF)';
        tooltip.style.border = '1px solid var(--color-border, #D1D5DB)';
        tooltip.style.fontFamily = 'inherit';
        tooltip.style.fontSize = 'inherit';
        tooltip.style.color = 'var(--color-text, #111827)';

        // Dark mode support
        if (document.body.classList.contains('dark-mode')) {
            tooltip.style.backgroundColor = 'var(--color-surface, #1F2937)';
            tooltip.style.color = 'var(--color-text, #F9FAFB)';
            tooltip.style.borderColor = 'var(--color-border, #374151)';
        }

        document.body.appendChild(tooltip);
        activeTooltip = tooltip;
        return tooltip;
    }

    function fallbackCopy(text, button) {
        // Fallback copy method using textarea
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        try {
            document.execCommand('copy');
            button.innerHTML = '<i class="fas fa-check"></i> Copied!';
            button.style.background = 'var(--color-success-light, #D1FAE5)';
            button.style.color = 'var(--color-success-dark, #065F46)';
            setTimeout(() => {
                button.innerHTML = '<i class="fas fa-copy"></i> Copy';
                button.style.background = 'var(--color-surface, #FFFFFF)';
                button.style.color = '';
            }, 2000);
        } catch (err) {
            console.error('Fallback copy failed:', err);
            button.innerHTML = '<i class="fas fa-times"></i> Failed';
        } finally {
            document.body.removeChild(textarea);
        }
    }

    function getRowIndex(cell) {
        const tr = cell.closest('tr');
        if (!tr) return -1;
        const tbody = tr.parentNode;
        return Array.prototype.indexOf.call(tbody.children, tr);
    }

    function getRowRect(tbody, idx) {
        const row = tbody.children[idx];
        return row ? row.getBoundingClientRect() : null;
    }

    function positionTooltip(cell, tooltip, rowIdx, tbody) {
        const cellRect = cell.getBoundingClientRect();
        const viewportWidth = window.innerWidth;
        const viewportHeight = window.innerHeight;

        tooltip.style.visibility = 'hidden';
        tooltip.style.left = '0';
        tooltip.style.top = '0';
        const tooltipRect = tooltip.getBoundingClientRect();
        tooltip.style.visibility = '';

        // Find min/max allowed row for popup
        const totalRows = tbody.children.length;
        const minRow = Math.max(0, rowIdx - 2);
        const maxRow = Math.min(totalRows - 1, rowIdx + 2);

        const minRowRect = getRowRect(tbody, minRow);
        const maxRowRect = getRowRect(tbody, maxRow);

        // Default: try to show below
        let left = cellRect.left + (cellRect.width - tooltipRect.width) / 2;
        if (left < 10) left = 10;
        if ((left + tooltipRect.width) > (viewportWidth - 10)) {
            left = viewportWidth - tooltipRect.width - 10;
        }

        // Try to show below, but not more than 2 rows below
        let top = cellRect.bottom + 8;
        let maxAllowedBottom = maxRowRect ? maxRowRect.bottom + 8 : viewportHeight - 10;
        if (top + tooltipRect.height > maxAllowedBottom) {
            // If not enough space below, try above (but not more than 2 rows above)
            top = cellRect.top - tooltipRect.height - 8;
            let minAllowedTop = minRowRect ? minRowRect.top - tooltipRect.height - 8 : 10;
            if (top < minAllowedTop) {
                top = minAllowedTop;
            }
        }

        // Clamp to viewport
        if (top < 10) top = 10;
        if (top + tooltipRect.height > viewportHeight - 10) {
            top = Math.max(10, viewportHeight - tooltipRect.height - 10);
        }

        tooltip.style.left = `${left}px`;
        tooltip.style.top = `${top}px`;
    }

    // Remove popup on any click outside
    function removeTooltip() {
        if (activeTooltip) {
            document.body.removeChild(activeTooltip);
            activeTooltip = null;
            activeCell = null;
            activeRowIdx = null;
            activeTbody = null;
        }
    }

    document.querySelectorAll('tbody').forEach(tbody => {
        tbody.addEventListener('click', function (e) {
            if (e.target.classList.contains('truncated-cell')) {
                removeTooltip();
                // Get the full text from the data attribute if present, else from the cell's parent td
                let fullText = '';
                if (e.target.parentElement && e.target.parentElement.__fullText !== undefined) {
                    fullText = e.target.parentElement.__fullText;
                } else if (e.target.dataset.fulltext) {
                    fullText = e.target.dataset.fulltext;
                } else {
                    // fallback: try to get from the only child text node of the parent td
                    fullText = e.target.textContent.endsWith('…')
                        ? e.target.textContent.slice(0, -1)
                        : e.target.textContent;
                }

                // Remove any binary indicators from display text but keep in tooltip
                fullText = fullText.replace(' [BIN]', '');

                const tooltip = createFloatingTooltip(fullText, e.target);
                const rowIdx = getRowIndex(e.target);
                positionTooltip(e.target, tooltip, rowIdx, tbody);
                activeTooltip = tooltip;
                activeCell = e.target;
                activeRowIdx = rowIdx;
                activeTbody = tbody;

                setTimeout(() => {
                    const range = document.createRange();
                    range.selectNodeContents(tooltip.querySelector('div:not(.encoding-info)') || tooltip);
                    const sel = window.getSelection();
                    sel.removeAllRanges();
                    sel.addRange(range);
                }, 10);

                document.addEventListener('click', function hideTooltip(ev) {
                    if (
                        activeTooltip &&
                        !activeTooltip.contains(ev.target) &&
                        ev.target !== activeCell
                    ) {
                        removeTooltip();
                        document.removeEventListener('click', hideTooltip);
                    }
                });
            }
        });
    });

    function updateTooltipOnScrollOrResize() {
        if (activeTooltip && activeTooltip.style.display === 'block' && activeCell && activeTbody != null && activeRowIdx != null) {
            positionTooltip(activeCell, activeTooltip, activeRowIdx, activeTbody);
        }
    }

    window.addEventListener('scroll', updateTooltipOnScrollOrResize, true);
    window.addEventListener('resize', updateTooltipOnScrollOrResize);
});

// --- Reusable Table Sorting for .unified-table ---
(function() {
    function makeTableSortable(table) {
        const ths = table.querySelectorAll('thead th');
        let sortCol = null, sortAsc = true;

        ths.forEach((th, idx) => {
            th.classList.add('sortable');
            th.style.cursor = 'pointer';
            th.addEventListener('click', function() {
                if (sortCol === idx) sortAsc = !sortAsc;
                else { sortCol = idx; sortAsc = true; }
                sortTable(table, idx, sortAsc);
                ths.forEach((h, i) => {
                    h.classList.remove('sorted-asc', 'sorted-desc');
                    const arrow = h.querySelector('.sort-arrow');
                    if (arrow) arrow.remove();
                    if (i === idx) {
                        h.classList.add(sortAsc ? 'sorted-asc' : 'sorted-desc');
                        const arrowSpan = document.createElement('span');
                        arrowSpan.className = 'sort-arrow';
                        arrowSpan.innerHTML = sortAsc ? ' ▲' : ' ▼';
                        h.appendChild(arrowSpan);
                    }
                });
            });
        });
    }

    function sortTable(table, colIdx, asc) {
        const tbody = table.tBodies[0];
        const rows = Array.from(tbody.rows);
        rows.sort((a, b) => {
            let va = a.cells[colIdx]?.textContent.trim() || '';
            let vb = b.cells[colIdx]?.textContent.trim() || '';
            // Try numeric sort if both values are numbers
            if (!isNaN(va) && !isNaN(vb) && va !== '' && vb !== '') {
                va = Number(va); vb = Number(vb);
            }
            if (va < vb) return asc ? -1 : 1;
            if (va > vb) return asc ? 1 : -1;
            return 0;
        });
        rows.forEach(row => tbody.appendChild(row));
    }

    document.addEventListener('DOMContentLoaded', function() {
        document.querySelectorAll('.unified-table').forEach(makeTableSortable);
    });
})();

// --- Sticky Controls Utility ---
function ensureStickyControls() {
    const controlsContainer = document.querySelector('.unified-controls-container');
    const mainContent = document.querySelector('.main-content');
    if (!controlsContainer) return;

    const paddingTop = parseFloat(getComputedStyle(mainContent).paddingTop) || 0;
    const controlsHeight = controlsContainer.getBoundingClientRect().height;
    const headerTop = controlsHeight + paddingTop;

    const tableHeaders = document.querySelectorAll('.unified-table thead th');
    tableHeaders.forEach(header => {
        header.style.position = 'sticky';
        // header.style.top = `0px`;
        header.style.zIndex = '99';
    });
}

// --- Table Refresh Controls (for both tables) ---
(function() {
    // Utility to get and set search/filter/sort state
    function getTableState(table) {
        return {
            search: document.querySelector('.unified-controls-container input[type="search"], .unified-controls-container input[type="text"]')?.value || '',
            filters: window.APP && window.APP.getActiveFilters ? window.APP.getActiveFilters() : {},
            sort: (() => {
                const th = table.querySelector('th.sorted-asc,th.sorted-desc');
                if (!th) return null;
                return {
                    idx: Array.from(th.parentNode.children).indexOf(th),
                    asc: th.classList.contains('sorted-asc')
                };
            })(),
            page: window.APP && window.APP.getCurrentPage ? window.APP.getCurrentPage() : 1
        };
    }
    function setTableState(table, state) {
        if (state.search) {
            const input = document.querySelector('.unified-controls-container input[type="search"], .unified-controls-container input[type="text"]');
            if (input) input.value = state.search;
        }
        if (window.APP && window.APP.applyFilters && state.filters) {
            window.APP.applyFilters(state.filters);
        }
        if (state.sort) {
            const ths = table.querySelectorAll('thead th');
            if (ths[state.sort.idx]) ths[state.sort.idx].click();
            if (!state.sort.asc && ths[state.sort.idx]) ths[state.sort.idx].click();
        }
        if (window.APP && window.APP.setCurrentPage && state.page) {
            window.APP.setCurrentPage(state.page);
        }
    }

    function addRefreshControls(container, table, fetchTableData) {
        // Only add once
        if (container.querySelector('.unified-refresh-controls')) return;
        const wrapper = document.createElement('div');
        wrapper.className = 'unified-refresh-controls';
        wrapper.style.display = 'flex';
        wrapper.style.alignItems = 'center';
        wrapper.style.gap = '8px';

        // Refresh button
        const refreshBtn = document.createElement('button');
        refreshBtn.className = 'unified-icon-button';
        refreshBtn.title = 'Refresh table now';
        refreshBtn.innerHTML = '<i class="fas fa-sync"></i>';
        wrapper.appendChild(refreshBtn);

        // Interval select
        const select = document.createElement('select');
        select.className = 'unified-select';
        select.title = 'Auto-refresh interval';
        select.innerHTML = `
            <option value="20000">20s</option>
            <option value="60000" selected>1m</option>
            <option value="300000">5m</option>
        `;
        wrapper.appendChild(document.createTextNode('Every'));
        wrapper.appendChild(select);

        // Error message area
        const errorMsg = document.createElement('span');
        errorMsg.className = 'unified-refresh-error';
        errorMsg.style.color = 'var(--color-danger)';
        errorMsg.style.marginLeft = '8px';
        errorMsg.style.display = 'none';
        wrapper.appendChild(errorMsg);

        container.appendChild(wrapper);

        let intervalId = null;
        let lastState = getTableState(table);
        let isRefreshing = false;

        function showError(msg) {
            errorMsg.textContent = msg;
            errorMsg.style.display = '';
            setTimeout(() => { errorMsg.style.display = 'none'; }, 5000);
        }

        function doRefresh() {
            if (isRefreshing) return;
            isRefreshing = true;
            refreshBtn.disabled = true;
            errorMsg.style.display = 'none';
            lastState = getTableState(table);
            Promise.resolve(fetchTableData())
                .then(() => {
                    setTableState(table, lastState);
                })
                .catch(err => {
                    showError('Refresh failed');
                    if (window.console && window.console.error) console.error('Table refresh failed:', err);
                })
                .finally(() => {
                    isRefreshing = false;
                    refreshBtn.disabled = false;
                });
        }

        refreshBtn.addEventListener('click', doRefresh);

        function setIntervalRefresh() {
            if (intervalId) clearInterval(intervalId);
            const ms = parseInt(select.value, 10);
            if (ms > 0) {
                intervalId = setInterval(doRefresh, ms);
            }
        }
        select.addEventListener('change', setIntervalRefresh);
        setIntervalRefresh();

        // Expose for cleanup if needed
        window.APP = window.APP || {};
        window.APP._refreshCleanup = () => { if (intervalId) clearInterval(intervalId); };
    }

    // Expose for page scripts
    window.APP = window.APP || {};
    window.APP.addRefreshControls = addRefreshControls;
})();

// Expose sticky controls utility for other scripts
window.APP = window.APP || {};
window.APP.ensureStickyControls = ensureStickyControls;