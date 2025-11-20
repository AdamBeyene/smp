package com.telemessage.simulators.smpp_cloudhopper.concatenation;

/**
 * Cloudhopper-native concatenation type enumeration.
 *
 * <p>Defines all SMPP message concatenation methods supported by Cloudhopper implementation.
 * This is independent of the legacy Logica implementation.</p>
 *
 * <p><b>Concatenation Methods:</b></p>
 * <ul>
 *   <li><b>DEFAULT</b> - No concatenation, single message</li>
 *   <li><b>TEXT_BASE</b> - Text-based pattern: "1/3 message text"</li>
 *   <li><b>UDHI</b> - User Data Header Indicator (esm_class=0x40, IEI 0x00/0x08)</li>
 *   <li><b>SAR</b> - Segmentation and Reassembly with TLV parameters</li>
 *   <li><b>PAYLOAD</b> - message_payload TLV for long messages (> 254 bytes)</li>
 *   <li><b>UDHI_PAYLOAD</b> - UDH header within message_payload TLV</li>
 * </ul>
 *
 * <p><b>SMPP Protocol Details:</b></p>
 * <ul>
 *   <li>UDHI: ESM class bit 6 set (0x40), UDH header with IEI 0x00 (8-bit ref) or 0x08 (16-bit ref)</li>
 *   <li>SAR: TLV tags 0x020C (ref num), 0x020E (total segments), 0x020F (segment seqnum)</li>
 *   <li>PAYLOAD: TLV tag 0x0424 (message_payload) for messages exceeding 254 octets</li>
 *   <li>UDHI cannot be mixed with SAR (mutually exclusive per SMPP spec)</li>
 * </ul>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-19
 * @see <a href="https://smpp.org/SMPP_v3_4_Issue1_2.pdf">SMPP v3.4 Specification</a>
 */
public enum CloudhopperConcatenationType {

    /**
     * No concatenation - standard single message.
     * Used for messages that fit within standard SMS limits (160 GSM7 or 70 UCS2 characters).
     */
    DEFAULT,

    /**
     * Text-based concatenation pattern.
     *
     * <p>Format: "part/total message text"</p>
     * <p>Example: "1/3 Hello World" (part 1 of 3)</p>
     *
     * <p>Pattern detection:</p>
     * <ul>
     *   <li>Regex: {@code ^(\d+)/(\d+)\s+(.*)$}</li>
     *   <li>Group 1: Part number (1-based)</li>
     *   <li>Group 2: Total parts</li>
     *   <li>Group 3: Actual message text</li>
     * </ul>
     *
     * <p>Reference number generation:</p>
     * <ul>
     *   <li>Hash of: source|destination|totalParts|textPreview</li>
     *   <li>Ensures same reference for all parts of one message</li>
     * </ul>
     */
    TEXT_BASE,

    /**
     * User Data Header Indicator (UDHI) concatenation.
     *
     * <p>SMPP Protocol:</p>
     * <ul>
     *   <li>ESM class: 0x40 (bit 6 set for UDHI)</li>
     *   <li>UDH structure: [UDHL][IEI][IEDL][IED...]</li>
     *   <li>IEI 0x00: 8-bit concatenation (reference 0-255)</li>
     *   <li>IEI 0x08: 16-bit concatenation (reference 0-65535)</li>
     * </ul>
     *
     * <p>UDH Header Format (8-bit):</p>
     * <pre>
     * Byte 0: UDHL (User Data Header Length) = 0x05
     * Byte 1: IEI (Information Element Identifier) = 0x00
     * Byte 2: IEDL (IE Data Length) = 0x03
     * Byte 3: Reference number (8-bit)
     * Byte 4: Total parts
     * Byte 5: Part number
     * Byte 6+: Message content
     * </pre>
     *
     * <p>Capacity impact:</p>
     * <ul>
     *   <li>GSM7: 160 → 153 characters per part (7 bytes UDH overhead)</li>
     *   <li>UCS2: 70 → 67 characters per part (6 bytes UDH overhead)</li>
     * </ul>
     */
    UDHI,

    /**
     * Segmentation and Reassembly (SAR) with TLV parameters.
     *
     * <p>SMPP TLV Tags:</p>
     * <ul>
     *   <li>0x020C (524): sar_msg_ref_num (2 bytes, reference 0-65535)</li>
     *   <li>0x020E (526): sar_total_segments (1 byte, total parts 1-255)</li>
     *   <li>0x020F (527): sar_segment_seqnum (1 byte, part number 1-255)</li>
     * </ul>
     *
     * <p>Advantages:</p>
     * <ul>
     *   <li>No UDH overhead - full 160/70 capacity per part</li>
     *   <li>Cleaner separation of protocol and content</li>
     * </ul>
     *
     * <p>Disadvantages:</p>
     * <ul>
     *   <li>Not all providers support SAR TLVs</li>
     *   <li>Some downstream systems may not reassemble correctly</li>
     * </ul>
     *
     * <p><b>Important:</b> Cannot be used with UDHI (mutually exclusive per SMPP v3.4 spec)</p>
     */
    SAR,

    /**
     * message_payload TLV for long messages.
     *
     * <p>SMPP TLV Tag:</p>
     * <ul>
     *   <li>0x0424 (1060): message_payload (variable length)</li>
     * </ul>
     *
     * <p>Usage:</p>
     * <ul>
     *   <li>For messages exceeding 254 octets</li>
     *   <li>sm_length must be set to NULL (0)</li>
     *   <li>short_message field must be empty</li>
     *   <li>Message content goes in message_payload TLV</li>
     * </ul>
     *
     * <p>Capacity:</p>
     * <ul>
     *   <li>Supports messages up to 64KB (TLV value length limit)</li>
     *   <li>No concatenation needed - sent as single PDU</li>
     * </ul>
     *
     * <p>Provider support:</p>
     * <ul>
     *   <li>Good support for modern SMSCs</li>
     *   <li>Some legacy systems may reject</li>
     *   <li>Always verify with provider</li>
     * </ul>
     */
    PAYLOAD,

    /**
     * UDHI header within message_payload TLV.
     *
     * <p>Hybrid approach combining UDHI and PAYLOAD:</p>
     * <ul>
     *   <li>ESM class: 0x40 (UDHI bit set)</li>
     *   <li>UDH header at start of message_payload TLV</li>
     *   <li>Useful for very long messages with concatenation</li>
     * </ul>
     *
     * <p>Structure:</p>
     * <pre>
     * message_payload TLV:
     *   [UDH Header (6-7 bytes)]
     *   [Message Content (rest)]
     * </pre>
     *
     * <p>Use case:</p>
     * <ul>
     *   <li>Individual parts exceed 254 bytes</li>
     *   <li>Need both concatenation AND long message support</li>
     *   <li>Example: Sending 1000-byte message in 2 parts of 500 bytes each</li>
     * </ul>
     *
     * <p><b>Note:</b> Limited provider support, use with caution</p>
     */
    UDHI_PAYLOAD;

    /**
     * Checks if this type uses UDHI (User Data Header Indicator).
     *
     * @return true if UDHI or UDHI_PAYLOAD
     */
    public boolean usesUdhi() {
        return this == UDHI || this == UDHI_PAYLOAD;
    }

    /**
     * Checks if this type uses TLV parameters.
     *
     * @return true if SAR, PAYLOAD, or UDHI_PAYLOAD
     */
    public boolean usesTlv() {
        return this == SAR || this == PAYLOAD || this == UDHI_PAYLOAD;
    }

    /**
     * Checks if this type requires message splitting.
     *
     * @return true if TEXT_BASE, UDHI, SAR, or UDHI_PAYLOAD
     */
    public boolean requiresSplitting() {
        return this == TEXT_BASE || this == UDHI || this == SAR || this == UDHI_PAYLOAD;
    }

    /**
     * Gets the ESM class byte for this concatenation type.
     *
     * @return ESM class byte (0x00 for default, 0x40 for UDHI-based)
     */
    public byte getEsmClass() {
        return usesUdhi() ? (byte) 0x40 : (byte) 0x00;
    }
}
