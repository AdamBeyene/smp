/**
 * Table Grouping Utilities
 * Provides reusable functions for displaying hierarchical/grouped data in tables
 * with expandable/collapsible rows
 */

(function(window) {
    'use strict';

    console.log('[DEBUG] table-grouping.js IIFE EXECUTING - START');
    console.log('[DEBUG] window object exists?', typeof window !== 'undefined');
    console.log('[DEBUG] window.TableGrouping before assignment:', window.TableGrouping);

    const TableGrouping = {
        /**
         * State management for expanded groups
         */
        expandedGroups: new Set(),

        /**
         * Render a table with grouped messages (concat messages with parts)
         * @param {Array} groupedData - Array of GroupedMessageResponse objects
         * @param {HTMLElement} tbody - Table body element to render into
         * @param {Array} columns - Column configuration array
         * @param {Object} options - Rendering options
         */
        renderGroupedMessages: function(groupedData, tbody, columns, options = {}) {
            console.log('[TableGrouping] renderGroupedMessages called');
            console.log('[TableGrouping] groupedData length:', groupedData ? groupedData.length : 'null');
            console.log('[TableGrouping] columns:', columns);
            console.log('[TableGrouping] tbody:', tbody);

            tbody.innerHTML = '';

            if (!groupedData || groupedData.length === 0) {
                console.log('[TableGrouping] No grouped data, showing placeholder');
                const tr = document.createElement('tr');
                const td = document.createElement('td');
                td.colSpan = columns.length;
                td.style.textAlign = 'center';
                td.textContent = 'No messages to display.';
                tr.appendChild(td);
                tbody.appendChild(tr);
                return;
            }

            console.log('[TableGrouping] Processing', groupedData.length, 'groups');
            groupedData.forEach((group, index) => {
                console.log('[TableGrouping] Group', index, 'type:', group.type);
                if (group.type === 'concat') {
                    // Render concat message group with parent and child rows
                    this.renderConcatGroup(group, tbody, columns, index, options);
                } else {
                    // Render single message as regular row
                    this.renderSingleMessage(group.message, tbody, columns, options);
                }
            });
            console.log('[TableGrouping] Finished rendering. tbody.children.length:', tbody.children.length);
        },

        /**
         * Render a concat message group (parent row + optional child rows)
         */
        renderConcatGroup: function(group, tbody, columns, groupIndex, options) {
            console.log('[TableGrouping] renderConcatGroup called for group', groupIndex);
            const groupId = `concat-group-${groupIndex}`;
            const isExpanded = this.expandedGroups.has(groupId);

            // Create parent row
            const parentRow = this.createParentRow(group, columns, groupId, isExpanded, options);
            console.log('[TableGrouping] Appending parent row for concat group', groupIndex);
            tbody.appendChild(parentRow);

            // Create child rows (parts) if expanded
            if (isExpanded && group.parts && group.parts.length > 0) {
                console.log('[TableGrouping] Appending', group.parts.length, 'child rows for concat group', groupIndex);
                group.parts.forEach((part, partIndex) => {
                    const childRow = this.createChildRow(part, columns, groupId, partIndex + 1, group.metadata.totalParts, options);
                    tbody.appendChild(childRow);
                });
            }
        },

        /**
         * Create a parent row for a concat message group
         */
        createParentRow: function(group, columns, groupId, isExpanded, options) {
            const tr = document.createElement('tr');
            tr.className = 'concat-parent-row';
            tr.dataset.groupId = groupId;

            const msg = group.message;
            const metadata = group.metadata;

            columns.forEach((col, idx) => {
                const td = document.createElement('td');

                // First column gets the expand/collapse icon
                if (idx === 0) {
                    const expandIcon = document.createElement('span');
                    expandIcon.className = 'expand-icon';
                    expandIcon.innerHTML = isExpanded ? '&#9660;' : '&#9654;'; // ▼ or ▶
                    expandIcon.style.cursor = 'pointer';
                    expandIcon.style.marginRight = '5px';
                    expandIcon.style.color = 'var(--color-primary, #3B82F6)';
                    expandIcon.addEventListener('click', (e) => {
                        e.stopPropagation();
                        this.toggleGroup(groupId, tbody, group, columns, options);
                    });
                    td.appendChild(expandIcon);
                }

                // Add cell content
                let val = msg[col] == null ? '' : String(msg[col]);

                // Special handling for text column - show full assembled text
                if (col === 'text') {
                    const textContainer = document.createElement('div');
                    textContainer.className = 'message-details';

                    const textSpan = document.createElement('span');
                    textSpan.className = 'message-text';
                    const maxLen = options.maxTruncate || 19;
                    if (val.length > maxLen) {
                        textSpan.classList.add('truncated-cell');
                        textSpan.title = "Click to expand";
                        textSpan.setAttribute('data-fulltext', val);
                        textSpan.textContent = val.substring(0, maxLen) + '…';
                    } else {
                        textSpan.textContent = val;
                    }
                    textContainer.appendChild(textSpan);

                    // Add concat badge
                    const badge = document.createElement('span');
                    badge.className = 'concat-badge';
                    const statusIcon = metadata.complete ? '\u2713' : '\u26A0'; // ✓ or ⚠
                    const statusClass = metadata.complete ? 'complete' : 'incomplete';
                    badge.classList.add(statusClass);
                    badge.textContent = `${statusIcon} CONCAT: ${metadata.receivedParts}/${metadata.totalParts}`;
                    badge.title = metadata.complete ?
                        `Complete concat message (${metadata.totalParts} parts)` :
                        `Incomplete: ${metadata.receivedParts} of ${metadata.totalParts} parts received`;
                    textContainer.appendChild(badge);

                    td.appendChild(textContainer);
                    td.__fullText = val;
                } else {
                    // Handle other columns normally
                    const textNode = document.createTextNode(val);
                    td.appendChild(textNode);
                }

                tr.appendChild(td);
            });

            return tr;
        },

        /**
         * Create a child row for a message part
         */
        createChildRow: function(part, columns, groupId, partNumber, totalParts, options) {
            const tr = document.createElement('tr');
            tr.className = 'concat-child-row';
            tr.dataset.parentGroup = groupId;

            columns.forEach((col, idx) => {
                const td = document.createElement('td');

                // Add indentation to first column
                if (idx === 0) {
                    td.style.paddingLeft = '30px';
                    const partLabel = document.createElement('span');
                    partLabel.className = 'part-label';
                    partLabel.textContent = `[${partNumber}/${totalParts}] `;
                    partLabel.style.color = 'var(--color-secondary, #6B7280)';
                    partLabel.style.fontSize = '0.85em';
                    td.appendChild(partLabel);
                }

                let val = part[col] == null ? '' : String(part[col]);

                // For text column in child rows, show just this part's text
                if (col === 'text') {
                    const textSpan = document.createElement('span');
                    textSpan.className = 'truncated-cell part-text';
                    const maxLen = options.maxTruncate || 40;
                    if (val.length > maxLen) {
                        textSpan.title = "Click to expand";
                        textSpan.setAttribute('data-fulltext', val);
                        textSpan.textContent = val.substring(0, maxLen) + '…';
                    } else {
                        textSpan.textContent = val;
                    }
                    td.appendChild(textSpan);
                    td.__fullText = val;
                } else {
                    const textNode = document.createTextNode(val);
                    td.appendChild(textNode);
                }

                tr.appendChild(td);
            });

            return tr;
        },

        /**
         * Render a single (non-concat) message
         */
        renderSingleMessage: function(msg, tbody, columns, options) {
            console.log('[TableGrouping] renderSingleMessage called for msg id:', msg ? msg.id : 'null');
            const tr = document.createElement('tr');
            tr.className = 'single-message-row';

            columns.forEach((col, idx) => {
                const td = document.createElement('td');
                let val = msg[col] == null ? '' : String(msg[col]);

                // Handle text column with truncation
                if (col === 'text') {
                    const textContainer = document.createElement('div');
                    textContainer.className = 'message-details';

                    const textSpan = document.createElement('span');
                    textSpan.className = 'message-text';
                    const maxLen = options.maxTruncate || 19;
                    if (val.length > maxLen) {
                        textSpan.classList.add('truncated-cell');
                        textSpan.title = "Click to expand";
                        textSpan.setAttribute('data-fulltext', val);
                        textSpan.textContent = val.substring(0, maxLen) + '…';
                    } else {
                        textSpan.textContent = val;
                    }
                    textContainer.appendChild(textSpan);

                    // Show binary indicator if present
                    if (msg.rawMessageBytesBase64) {
                        const binaryIndicator = document.createElement('span');
                        binaryIndicator.className = 'binary-indicator';
                        binaryIndicator.textContent = '[BIN]';
                        textContainer.appendChild(binaryIndicator);
                    }

                    td.appendChild(textContainer);
                    td.__fullText = val;
                    td.title = msg.messageEncoding ? `Encoding: ${msg.messageEncoding}` : '';
                    if (msg.rawMessageBytesBase64) {
                        td.dataset.hasBinary = 'true';
                    }
                } else if ([2, 5, 7, 8, 9, 10, 12].includes(idx)) {
                    // Expandable columns
                    if (val.length > 60) {
                        td.innerHTML = `<span class="truncated-cell" title="Click to expand" data-fulltext="${this.escapeHtml(val)}">${this.escapeHtml(val.substring(0, 60))}…</span>`;
                        td.__fullText = val;
                    } else {
                        td.innerHTML = `<span class="truncated-cell">${this.escapeHtml(val)}</span>`;
                        td.__fullText = val;
                    }
                } else {
                    td.textContent = val;
                }

                tr.appendChild(td);
            });

            console.log('[TableGrouping] Appending single message row to tbody');
            tbody.appendChild(tr);
        },

        /**
         * Toggle expand/collapse state of a group
         */
        toggleGroup: function(groupId, tbody, group, columns, options) {
            const isExpanded = this.expandedGroups.has(groupId);

            if (isExpanded) {
                // Collapse: remove child rows
                this.expandedGroups.delete(groupId);
                const childRows = tbody.querySelectorAll(`tr[data-parent-group="${groupId}"]`);
                childRows.forEach(row => row.remove());

                // Update icon
                const parentRow = tbody.querySelector(`tr[data-group-id="${groupId}"]`);
                if (parentRow) {
                    const icon = parentRow.querySelector('.expand-icon');
                    if (icon) icon.innerHTML = '&#9654;'; // ▶
                }
            } else {
                // Expand: add child rows
                this.expandedGroups.add(groupId);
                const parentRow = tbody.querySelector(`tr[data-group-id="${groupId}"]`);
                if (parentRow && group.parts && group.parts.length > 0) {
                    group.parts.forEach((part, partIndex) => {
                        const childRow = this.createChildRow(part, columns, groupId, partIndex + 1, group.metadata.totalParts, options);
                        parentRow.insertAdjacentElement('afterend', childRow);
                    });

                    // Update icon
                    const icon = parentRow.querySelector('.expand-icon');
                    if (icon) icon.innerHTML = '&#9660;'; // ▼
                }
            }
        },

        /**
         * Expand all concat groups
         */
        expandAll: function(groupedData, tbody, columns, options) {
            groupedData.forEach((group, index) => {
                if (group.type === 'concat') {
                    const groupId = `concat-group-${index}`;
                    if (!this.expandedGroups.has(groupId)) {
                        this.expandedGroups.add(groupId);
                    }
                }
            });
            this.renderGroupedMessages(groupedData, tbody, columns, options);
        },

        /**
         * Collapse all concat groups
         */
        collapseAll: function(groupedData, tbody, columns, options) {
            this.expandedGroups.clear();
            this.renderGroupedMessages(groupedData, tbody, columns, options);
        },

        /**
         * Helper to escape HTML
         */
        escapeHtml: function(str) {
            if (!str) return '';
            return str
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        }
    };

    // Expose to global scope
    console.log('[DEBUG] BEFORE window.TableGrouping assignment');
    console.log('[DEBUG] TableGrouping object:', TableGrouping);
    console.log('[DEBUG] typeof TableGrouping:', typeof TableGrouping);

    window.TableGrouping = TableGrouping;

    console.log('[DEBUG] AFTER window.TableGrouping assignment');
    console.log('[DEBUG] window.TableGrouping:', window.TableGrouping);
    console.log('[DEBUG] typeof window.TableGrouping:', typeof window.TableGrouping);
    console.log('TableGrouping module loaded successfully', window.TableGrouping);

})(window);
