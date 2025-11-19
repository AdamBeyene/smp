/**
 * Table Export Utilities
 * Provides CSV and JSON export functionality for messages and connections tables
 */

(function(window) {
    'use strict';

    console.log('[DEBUG] table-export.js IIFE EXECUTING - START');
    console.log('[DEBUG] window object exists?', typeof window !== 'undefined');
    console.log('[DEBUG] window.TableExport before assignment:', window.TableExport);

    const TableExport = {
        /**
         * Export messages to CSV
         * @param {Array} messages - Array of message objects
         * @param {string} filename - Output filename (without extension)
         */
        exportMessagesToCSV: function(messages, filename = 'messages') {
            if (!messages || messages.length === 0) {
                alert('No messages to export');
                return;
            }

            // Define columns to export
            const columns = [
                { key: 'id', label: 'Message ID' },
                { key: 'providerId', label: 'Provider ID' },
                { key: 'from', label: 'From' },
                { key: 'to', label: 'To' },
                { key: 'dir', label: 'Direction' },
                { key: 'text', label: 'Message Text' },
                { key: 'messageTime', label: 'Message Time' },
                { key: 'messageEncoding', label: 'Encoding' },
                { key: 'partNumber', label: 'Part Number' },
                { key: 'totalParts', label: 'Total Parts' },
                { key: 'referenceNumber', label: 'Reference Number' },
                { key: 'deliveryReceiptTime', label: 'DR Time' }
            ];

            // Create CSV header
            const header = columns.map(col => this.escapeCSV(col.label)).join(',');

            // Create CSV rows
            const rows = messages.map(msg => {
                return columns.map(col => {
                    let value = msg[col.key];
                    // Handle null/undefined
                    if (value === null || value === undefined) {
                        return '';
                    }
                    // Convert to string and escape
                    return this.escapeCSV(String(value));
                }).join(',');
            });

            // Combine header and rows
            const csv = [header, ...rows].join('\n');

            // Trigger download
            this.downloadFile(csv, `${filename}.csv`, 'text/csv;charset=utf-8;');
        },

        /**
         * Export connections to CSV
         * @param {Array} connections - Array of connection objects
         * @param {string} filename - Output filename (without extension)
         */
        exportConnectionsToCSV: function(connections, filename = 'connections') {
            if (!connections || connections.length === 0) {
                alert('No connections to export');
                return;
            }

            const columns = [
                { key: 'id', label: 'ID' },
                { key: 'name', label: 'Name' },
                { key: 'transmitterState', label: 'TX State' },
                { key: 'receiverState', label: 'RX State' },
                { key: 'transmitterType', label: 'TX Type' },
                { key: 'receiverType', label: 'RX Type' },
                { key: 'transmitterPort', label: 'TX Port' },
                { key: 'receiverPort', label: 'RX Port' },
                { key: 'transmitterHost', label: 'TX Host' },
                { key: 'receiverHost', label: 'RX Host' }
            ];

            const header = columns.map(col => this.escapeCSV(col.label)).join(',');

            const rows = connections.map(conn => {
                return columns.map(col => {
                    let value = conn[col.key];
                    if (value === null || value === undefined) {
                        return '';
                    }
                    return this.escapeCSV(String(value));
                }).join(',');
            });

            const csv = [header, ...rows].join('\n');
            this.downloadFile(csv, `${filename}.csv`, 'text/csv;charset=utf-8;');
        },

        /**
         * Export messages to JSON
         * @param {Array} messages - Array of message objects
         * @param {string} filename - Output filename (without extension)
         * @param {boolean} includeRawBytes - Include base64 encoded raw bytes
         */
        exportMessagesToJSON: function(messages, filename = 'messages', includeRawBytes = false) {
            if (!messages || messages.length === 0) {
                alert('No messages to export');
                return;
            }

            // Optionally filter out raw bytes for smaller file size
            let exportData = messages;
            if (!includeRawBytes) {
                exportData = messages.map(msg => {
                    const { rawMessageBytes, rawMessageBytesBase64, ...rest } = msg;
                    return rest;
                });
            }

            const json = JSON.stringify(exportData, null, 2);
            this.downloadFile(json, `${filename}.json`, 'application/json;charset=utf-8;');
        },

        /**
         * Export connections to JSON
         * @param {Array} connections - Array of connection objects
         * @param {string} filename - Output filename (without extension)
         */
        exportConnectionsToJSON: function(connections, filename = 'connections') {
            if (!connections || connections.length === 0) {
                alert('No connections to export');
                return;
            }

            const json = JSON.stringify(connections, null, 2);
            this.downloadFile(json, `${filename}.json`, 'application/json;charset=utf-8;');
        },

        /**
         * Export grouped messages (for concat messages)
         * @param {Array} groupedData - Array of GroupedMessageResponse objects
         * @param {string} filename - Output filename
         */
        exportGroupedMessagesToJSON: function(groupedData, filename = 'grouped-messages') {
            if (!groupedData || groupedData.length === 0) {
                alert('No grouped messages to export');
                return;
            }

            const json = JSON.stringify(groupedData, null, 2);
            this.downloadFile(json, `${filename}.json`, 'application/json;charset=utf-8;');
        },

        /**
         * Escape CSV value
         * @param {string} value - Value to escape
         * @returns {string} Escaped value
         */
        escapeCSV: function(value) {
            if (value === null || value === undefined) {
                return '';
            }
            value = String(value);
            // If value contains comma, newline, or quote, wrap in quotes and escape quotes
            if (value.includes(',') || value.includes('\n') || value.includes('"')) {
                value = '"' + value.replace(/"/g, '""') + '"';
            }
            return value;
        },

        /**
         * Trigger file download
         * @param {string} content - File content
         * @param {string} filename - Filename
         * @param {string} mimeType - MIME type
         */
        downloadFile: function(content, filename, mimeType) {
            const blob = new Blob([content], { type: mimeType });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);
        },

        /**
         * Show export dialog for messages
         * @param {Array} messages - Messages to export
         * @param {string} context - 'all', 'visible', or 'filtered'
         */
        showExportDialog: function(messages, context = 'all') {
            const dialog = document.createElement('div');
            dialog.className = 'export-dialog-overlay';
            dialog.innerHTML = `
                <div class="export-dialog">
                    <div class="export-dialog-header">
                        <h3>Export ${context.charAt(0).toUpperCase() + context.slice(1)} Messages</h3>
                        <button class="dialog-close-btn" onclick="this.closest('.export-dialog-overlay').remove()">&times;</button>
                    </div>
                    <div class="export-dialog-body">
                        <p><strong>${messages.length}</strong> message(s) will be exported.</p>
                        <div class="export-options">
                            <label>
                                <input type="radio" name="export-format" value="csv" checked>
                                CSV (Comma-Separated Values)
                            </label>
                            <label>
                                <input type="radio" name="export-format" value="json">
                                JSON (with metadata)
                            </label>
                        </div>
                        <div class="export-json-options" style="display: none; margin-top: 10px;">
                            <label>
                                <input type="checkbox" id="include-raw-bytes">
                                Include raw binary data (Base64 encoded)
                            </label>
                        </div>
                    </div>
                    <div class="export-dialog-footer">
                        <button class="unified-button unified-button-secondary" onclick="this.closest('.export-dialog-overlay').remove()">Cancel</button>
                        <button class="unified-button unified-button-primary" id="export-confirm-btn">Export</button>
                    </div>
                </div>
            `;

            // Add styles
            dialog.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background: rgba(0, 0, 0, 0.5);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 10000;
            `;

            document.body.appendChild(dialog);

            // Toggle JSON options visibility
            const formatRadios = dialog.querySelectorAll('input[name="export-format"]');
            const jsonOptions = dialog.querySelector('.export-json-options');
            formatRadios.forEach(radio => {
                radio.addEventListener('change', () => {
                    jsonOptions.style.display = radio.value === 'json' ? 'block' : 'none';
                });
            });

            // Handle export
            const confirmBtn = dialog.querySelector('#export-confirm-btn');
            confirmBtn.addEventListener('click', () => {
                const format = dialog.querySelector('input[name="export-format"]:checked').value;
                const timestamp = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19);
                const filename = `messages-${context}-${timestamp}`;

                if (format === 'csv') {
                    this.exportMessagesToCSV(messages, filename);
                } else {
                    const includeRawBytes = dialog.querySelector('#include-raw-bytes').checked;
                    this.exportMessagesToJSON(messages, filename, includeRawBytes);
                }

                dialog.remove();
            });
        }
    };

    // Expose to global scope
    console.log('[DEBUG] BEFORE window.TableExport assignment');
    console.log('[DEBUG] TableExport object:', TableExport);
    console.log('[DEBUG] typeof TableExport:', typeof TableExport);

    window.TableExport = TableExport;

    console.log('[DEBUG] AFTER window.TableExport assignment');
    console.log('[DEBUG] window.TableExport:', window.TableExport);
    console.log('[DEBUG] typeof window.TableExport:', typeof window.TableExport);
    console.log('TableExport module loaded successfully', window.TableExport);

})(window);
