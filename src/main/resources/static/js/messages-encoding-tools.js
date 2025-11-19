/**
 * Message encoding visualization tools
 */
class MessageEncodingTools {
    /**
     * Analyze message for potential encoding issues
     */
    static analyzeEncoding(text, encoding) {
        const issues = [];

        // Check for characters that might cause issues in GSM7
        if (encoding === 'GSM7') {
            const gsm7ExtendedChars = '^{}\\[~]|€';
            const nonGsm7Chars = [];

            for (let i = 0; i < text.length; i++) {
                const char = text.charAt(i);
                // Check if character is in GSM7 alphabet
                if (!this.isGsm7Compatible(char)) {
                    nonGsm7Chars.push(`'${char}' (${char.charCodeAt(0).toString(16)})`);
                }
                // Check if character is from GSM7 extended set (counts as 2 chars)
                else if (gsm7ExtendedChars.includes(char)) {
                    issues.push(`Character '${char}' is from GSM7 extended set and counts as 2 characters`);
                }
            }

            if (nonGsm7Chars.length > 0) {
                issues.push(`Found ${nonGsm7Chars.length} non-GSM7 characters: ${nonGsm7Chars.join(', ')}`);
            }
        }

        // Calculate message parts
        const charCount = text.length;
        let partsCount = 1;

        if (encoding === 'GSM7') {
            partsCount = charCount <= 160 ? 1 : Math.ceil(charCount / 153);
            issues.push(`Message length: ${charCount} characters (${partsCount} parts)`);
            if (partsCount > 1) {
                issues.push(`Part boundaries at: ${this.calculatePartBoundaries(charCount, 153).join(', ')}`);
            }
        } else if (encoding === 'UTF-16BE' || encoding === 'UCS-2') {
            partsCount = charCount <= 70 ? 1 : Math.ceil(charCount / 67);
            issues.push(`Message length: ${charCount} characters (${partsCount} parts)`);
            if (partsCount > 1) {
                issues.push(`Part boundaries at: ${this.calculatePartBoundaries(charCount, 67).join(', ')}`);
            }
        } else {
            issues.push(`Message length: ${charCount} characters`);
        }

        return issues;
    }

    static isGsm7Compatible(char) {
        const gsm7Alphabet =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\x1BÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
        const gsm7Ext = "^{}\\[~]|€";

        return gsm7Alphabet.includes(char) || gsm7Ext.includes(char);
    }

    static calculatePartBoundaries(totalChars, charsPerPart) {
        const boundaries = [];
        for (let i = charsPerPart; i < totalChars; i += charsPerPart) {
            boundaries.push(i);
        }
        return boundaries;
    }

    /**
     * Display message in hexadecimal format
     */
    static textToHex(text) {
        let hex = '';
        for (let i = 0; i < text.length; i++) {
            const charCode = text.charCodeAt(i);
            hex += charCode.toString(16).padStart(4, '0') + ' ';
        }
        return hex.trim();
    }
}
