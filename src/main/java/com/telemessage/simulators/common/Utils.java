package com.telemessage.simulators.common;



import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class Utils {

    /**
     * a Random instance to be used for all your Random needs
     */
    public static final Random random = new Random();

    final private static char[] keyDigitsAndChars =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnop".toCharArray();
    final private static char[] keyDigits = "0123456789".toCharArray();


    /**
     * Generate a random key String.
     *
     * @param length     of the genereated key
     * @param useLetters if true the key can include letters, otherwise it includes only digits.
     * @return the generated key String
     */
    public static String generateRandomKey(int length, boolean useLetters) {
        return generateRandomKey(length, useLetters ? keyDigitsAndChars : keyDigits);
    }

    /**
     * Generate a random key String.
     *
     * @param length     of the genereated key
     * @param alphabet if true the key can include letters, otherwise it includes only digits.
     * @return the generated key String
     */
    public static String generateRandomKey(int length, char[] alphabet) {
        int dataLength = alphabet.length;
        char[] key = new char[length];
        for (int i = 0; i < length; i++)
            key[i] = alphabet[Utils.random.nextInt(dataLength)];
        return new String(key);
    }

    /**
     * Checks whether the given string can be fully displayed in given encoding.
     *
     * @param str a String to be tested.
     * @param enc a character encoding to test with.
     * @return true if str can be displayed in enc.
     */
    public static boolean canBeDisplayedInEnc(String str, String enc)
             {
        // if str can't be displayed in enc, the unsupported characters
        // will be lost in conversion (turned into '?' or other placeholder).
        try {
            return str.equals(new String(str.getBytes(enc), enc));
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    public static boolean canBeDisplayedInEncNEW(CharSequence str, String enc) {
//        // Check for GSM7 encoding
//        if (enc.equalsIgnoreCase("GSM7")) {
//            return checkGSM7(str);
//        }

        // For other encodings, use the Java Charset approach
        try {
            Charset charset = Charset.forName(enc);
            CharsetDecoder decoder = charset.newDecoder();
            decoder.decode(java.nio.ByteBuffer.wrap(str.toString().getBytes(enc)));
            // Try to decode the string back into the original string
            return true;  // The string can be displayed in this encoding
        } catch (CharacterCodingException | UnsupportedEncodingException e) {
            log.info("canBeDisplayedInEnc UnsupportedEncodingException/CharacterCodingException return false");
            return false;  // The string can't be displayed in this encoding
        }
    }
    static Set<Character> gsm7AlphabetSet = new HashSet<>();

    static {
        String gsm7Alphabet =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                        "abcdefghijklmnopqrstuvwxyz" +
                        "0123456789" +
                        "¡¢£¤¥¦§¨©ª«¬®¯°±²³´µ¶·¸¹º»¼½¾¿" +
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                        "abcdefghijklmnopqrstuvwxyz" +
                        "0123456789" +
                        "¡\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~€";
        for (char ch : gsm7Alphabet.toCharArray()) {
            gsm7AlphabetSet.add(ch);
        }
    }

    // Main method for encoding check


    // Optimized check for GSM7 encoding
    public static boolean checkGSM7(CharSequence str) {
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (!gsm7AlphabetSet.contains(ch)) {
                return false;
            }
        }
        return true;  // All characters can be displayed in GSM 7 encoding
    }


    /**
     * Encodes a string to UCS-2 format. In UCS-2 format, the entire SMS message is sent as a sequence of hexencoded
     * Unicode characters. Each Unicode character is represented as four hex digits (0..9, A..F in uppercase). For example,
     * the string "ABC(alpha)(beta)" consists of the following Unicode characters (in hexadecimal) A = 41, B = 42, C = 43,
     * a = 3B1, b = 3B2 The message string to send in the message field is 00410042004303B103B2
     */
    public static String encodeUCS2(String s) {
        Formatter formatter = new Formatter();
        for (int i = 0; i < s.length(); i++)
            formatter.format("%04x", (int)s.charAt(i));
        return formatter.toString();
    }

    /**
     * Encodes a byte array to UCS-2 format, where each byte is represented by two consecutive hex bytes, the first is 00
     * and the second is the byte. In UCS-2 format, the entire SMS message is sent as a sequence of hexencoded Unicode
     * characters. Each Unicode character is represented as four hex digits (0..9, A..F in uppercase). For example, the
     * string "ABC(alpha)(beta)" consists of the following Unicode characters (in hexadecimal) A = 41, B = 42, C = 43, a =
     * 3B1, b = 3B2 The message string to send in the message field is 00410042004303B103B2
     */
    public static String encodeUCS2(byte[] bytes) {
        Formatter formatter = new Formatter();
        for (byte b : bytes)
            formatter.format("%04x", b);
        return formatter.toString();
    }

    public static List<String> splitByLength(String message, int length) {
        List<String> messages = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int i=0;
        for (; i<message.length(); i++) {
            char c = message.charAt(i);
            if (i % length == 0) {
                if (sb.length() != 0)
                    messages.add(sb.toString());
                sb = new StringBuilder();
            }
            sb.append(c);
        }
        if ((i-1) % length != 0)
            messages.add(sb.toString());
        return messages;
    }

    public static List<String> split(String message, int length) {
        final char SPLIT_1 = ' ';
        final char SPLIT_2 = '\n';
        final char SPLIT_3 = '\t';

        List<String> messages = new ArrayList<>();

        StringBuilder all = new StringBuilder();
        StringBuilder space = new StringBuilder();
        int count = 0;

        // Loop through each character in the message
        for (char c : message.toCharArray()) {
            // Check if the character is a space, newline, tab, or the last character
            if (c == SPLIT_1 || c == SPLIT_2 || c == SPLIT_3 || count == message.length() - 1) {
                // If combined length of 'all' and 'space' is less than the limit
                if (all.length() + space.length() < length - 1) {
                    // Append accumulated space content directly
                    all.append(space);
                    all.append(c == SPLIT_2 ? c : ' ');  // Handle newline vs space
                    space.setLength(0);  // Reset space after appending
                } else {
                    // Add the current content in 'all' to the messages list
                    messages.add(all.toString());
                    all.setLength(0);  // Clear the 'all' StringBuilder

                    // Split 'space' if its length exceeds the allowed length
                    int remainingSpaceLength = space.length();
                    if (remainingSpaceLength > length) {
                        while (remainingSpaceLength > length) {
                            messages.add(space.substring(0, length));  // Add a chunk of space
                            space.delete(0, length);  // Remove the chunk
                            remainingSpaceLength = space.length();  // Update the remaining length
                        }
                    }
                    // Append remaining space content to 'all'
                    all.append(space);
                    space.setLength(0);  // Clear 'space' after appending
                }
            } else {
                // Accumulate the character in 'space'
                space.append(c);
            }
            count++;
        }

        // After the loop, append any remaining content from 'space' and 'all'
        if (space.length() > 0) {
            all.append(space);
        }
        if (all.length() > 0) {
            messages.add(all.toString());  // Add the final part
        }

        return messages;
    }

    public static List<String> splitOld(String message, int length) {
        final char SPLIT_1 = ' ';
        final char SPLIT_2 = '\n';
        final char SPLIT_3 = '\t';

        List<String> messages = new ArrayList<>();

        StringBuilder all = new StringBuilder();
        StringBuilder space = new StringBuilder();
        int count = 0;
        for (char c : message.toCharArray()) {
            if (c == SPLIT_1 || c == SPLIT_2 || c == SPLIT_3 || count == message.length() - 1) {
                if (all.length() + space.length() < length - 1) { // count spce => -1
                    all.append(space.toString());
                    if (c == SPLIT_2)
                        all.append(c);
                    else
                        all.append(' ');
                    space.setLength(0);
                    space.trimToSize();
                } else {
                    messages.add(all.toString());
                    all.setLength(0);
                    all.trimToSize();
                    if (space.length() > length) {
                        String[] longText = space.toString().split("(.+){" + length + "," + length + "}");
                        int i=0;
                        for (; i<longText.length-1; i++) {
                            messages.add(longText[i]);
                        }
                        space.setLength(0);
                        space.trimToSize();
                        space.append(longText[i]); // lets add the last one to space string builder
                    } else {
                        all.append(space.toString());
                        space.setLength(0);
                        space.trimToSize();
                    }
                }
            } else {
                space.append(c);
            }
            count++;
        }
        all.append(space.toString());
        if (all.length() != 0)
            messages.add(all.toString());
        return messages;
    }

    public static <T> Collection<T> deNull(Collection<T> list) {
        return list == null ? new ArrayList<T>() : list;
    }

    public static <T> List<T> deNull(List<T> list) {
        return list == null ? new ArrayList<T>() : list;
    }

    public static <K,V> Map<K,V> deNull(Map<K,V> map) {
        return map == null ? new HashMap<K,V>() : map;
    }

    public static <K,V> Map<K,V> deNullConcurrent(Map<K,V> map) {
        return map == null ? new ConcurrentHashMap<K,V>() : map;
    }
}
