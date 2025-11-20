
import com.telemessage.simulators.common.Utils;
import com.telemessage.simulators.common.conf.CombinedCharsetProvider;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import com.telemessage.simulators.smpp.SimUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.telemessage.simulators.common.JSONUtils.toJSON;
import static com.telemessage.simulators.smpp.SMPPConnection.*;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
//@SpringBootTest(classes = com.telemessage.simulators.TM_QA_SMPP_SIMULATOR_Application.class)
//@ContextConfiguration(classes = com.telemessage.simulators.TM_QA_SMPP_SIMULATOR_Application.class) // Specify your test-specific configuration here
public class TextTests {


    MessagesCache cacheService;

    @Test
    public void testCharsetResolution() {
        log.info("Testing charset resolution for custom encodings...");

        // Test encodings that should be available through our custom charset provider
        String[] testEncodings = {"SCGSM", "GSM7", "CCGSM", "UTF-8", "ISO-8859-1"};

        for (String encoding : testEncodings) {
            try {
                Charset charset = Charset.forName(encoding);
                assertNotNull(charset, "Charset should not be null for encoding: " + encoding);
                log.info("✓ Successfully resolved charset: {} -> {}", encoding, charset.name());

                // Test basic encoding/decoding
                String testText = "Hello World! Test 123";
                byte[] encoded = testText.getBytes(charset);
                String decoded = new String(encoded, charset);
                assertEquals(testText, decoded, "Round-trip encoding/decoding should work for: " + encoding);
                log.info("✓ Round-trip encoding/decoding works for: {}", encoding);

            } catch (Exception e) {
                log.error("✗ Failed to resolve or use charset: {} - Error: {}", encoding, e.getMessage());
                // Don't fail the test immediately, log and continue to see all results
            }
        }

        // Test specific GSM7 characters
        testGSM7SpecificCharacters();

        // Test Utils.canBeDisplayedInEnc with custom charsets
        testUtilsWithCustomCharsets();
    }

    private void testGSM7SpecificCharacters() {
        log.info("Testing GSM7-specific character handling...");

        try {
            String gsmText = "Hello @£$¥èéùìòÇ"; // Mix of basic and GSM7-specific chars

            // Test with different GSM7 encoding names
//            String[] gsmEncodings = {"GSM7", "SCGSM", "CCGSM"};
            List<String> gsmEncodings = List.of("GSM7", "SCGSM", "CCGSM");
            for (String encoding : gsmEncodings) {
                try {
                    Charset charset = Charset.forName(encoding);
                    byte[] encoded = gsmText.getBytes(charset);
                    String decoded = new String(encoded, charset);
                    log.info("✓ GSM7 encoding '{}' handled text: '{}' -> '{}'", encoding, gsmText, decoded);
                } catch (Exception e) {
                    log.warn("✗ GSM7 encoding '{}' failed: {}", encoding, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error testing GSM7 characters: {}", e.getMessage());
        }
    }

    private void testUtilsWithCustomCharsets() {
        log.info("Testing Utils.canBeDisplayedInEnc with custom charsets...");

        String testText = "Hello World!";
        String unicodeText = "Hello 🌍 世界!";

        String[] encodings = {"GSM7", "SCGSM", "CCGSM", "UTF-8", "ISO-8859-1"};

        for (String encoding : encodings) {
            try {
                boolean canDisplayBasic = Utils.canBeDisplayedInEnc(testText, encoding);
                boolean canDisplayUnicode = Utils.canBeDisplayedInEnc(unicodeText, encoding);

                log.info("✓ Utils.canBeDisplayedInEnc('{}', '{}'): basic={}, unicode={}",
                        encoding, testText, canDisplayBasic, canDisplayUnicode);

                // Basic ASCII should work in most encodings
                if (encoding.equals("UTF-8") || encoding.equals("ISO-8859-1")) {
                    Assertions.assertTrue(canDisplayBasic, "Basic text should be displayable in " + encoding);
                }

            } catch (Exception e) {
                log.warn("✗ Utils.canBeDisplayedInEnc failed for '{}': {}", encoding, e.getMessage());
            }
        }
    }

    String longText = "START LONG TEXTT {{current_date}} \\n1: Eng\\nabcdefghijklmnopqrstuvwxyz\\n2: Eng Caps\\naAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ\\n3: Hebrew\\nאבבּגדהוזחטיכךכּלמםנןסעפףפּצץקרששׁשׂת\\n4: Spanish\\n¡¿ÁÉÍÑÓÚÜáéíñóúü\\n5: Arabic\\nءآأؤإئابةتثجحخدذرزسشصضطظعغػؼؽؾؿـفقكلمنهوىي\\n6: Chinese\\n诶比西迪伊艾弗吉艾尺艾杰开艾勒艾马艾娜哦屁吉吾艾儿艾丝提伊吾维豆贝尔维艾克斯吾艾贼德\\n7: Japanese.1\\nあぁかさたなはまやゃらわがざだばぱいぃきしちにひみりゐぎじぢびぴうぅくすつぬふむゆゅるぐずづぶぷえ\\n7: Japanese.2\\nぇけせてねへめれゑげぜでべぺおぉこそとのほもよょろをごぞどぼぽゔっんーゝゞ、。\\n8: French\\nÀàÂâÆæÇçÉéÈèÊêËëÎîÏïÔôŒœÙùÛûÜüŸÿ«”“—»–’…·@¼½¾€\\n9: Russian.1\\nАаБбВвГгДдЕеЁёЖжЗзИиЙйКкЛлМмНнОоПпРрСсТтУуФфХх\\n9: Russian.2\\nЦцЧчШшЩщЪъЫыЬьЭэЮюЯяіІѣѢѳѲѵѴ\\n10: Currecny\\n$¢£€¥₹₽元¤₠₡₢₣₤₥₦₧₨₩₪₫₭₮₯₰₱₲₳₴₵₶₸₺₼₿৲৳૱௹฿៛㍐円圆圎圓圜원﷼＄￠￡￥￦\\n11: iso full\\nSP!\\\"#$%&'()*+,-.\\/0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~NBSP¡¢£¤¥¦§¨©ª«¬SHY®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏĞÑÒÓÔÕÖ×ØÙÚÛÜİŞßàáâãäåæçèéêëìíîïğñòóôõö÷øùúûüışÿ\\n12: Emojis.1\\n\uD83D\uDE03\uD83D\uDE04\uD83D\uDE01\uD83D\uDE06\uD83D\uDE05\uD83E\uDD23\uD83D\uDE02\uD83D\uDE42\uD83D\uDE43\uD83D\uDE09\uD83D\uDE0A\uD83D\uDE07\uD83E\uDD70\uD83D\uDE0D\uD83E\uDD29\uD83D\uDE18\uD83D\uDE17☺\uFE0F\uD83D\uDE1A\uD83D\uDE19\uD83D\uDE0B\uD83D\uDE1B\uD83D\uDE1C\uD83E\uDD2A\uD83D\uDE1D\uD83E\uDD11\uD83E\uDD17\uD83E\uDD2D\uD83E\uDD2B\uD83E\uDD14\uD83E\uDD10\uD83E\uDD28\uD83D\uDE10\\n12: Emojis.2\\n\uD83D\uDC7B\uD83D\uDC7D\uD83D\uDC7E\uD83E\uDD16\uD83D\uDE3A\uD83D\uDE38\uD83D\uDE39\uD83D\uDE3B\uD83D\uDE3C\uD83D\uDE3D\uD83D\uDE40\uD83D\uDE3F\uD83D\uDE3E\uD83D\uDE48\uD83D\uDE49\uD83D\uDE4A\uD83D\uDC8B\uD83D\uDC8C\uD83D\uDC98\uD83D\uDC9D\uD83D\uDC96\uD83D\uDC97\uD83D\uDC93\uD83D\uDC9E\uD83D\uDC95\uD83D\uDC9F❣\uFE0F\uD83D\uDC94❤\uFE0F\u200D\uD83D\uDD25❤\uFE0F\u200D\uD83E\uDE79\\nEND LONG TEXT. Sent at {{current_date}}";

    @Test
    public void testSplitTestASCII_CONCAT_LENGTH() {
        // Define the maximum length of each split message

        // Get results from both methods
        List<String> optimizedResult = Utils.split(longText + "_" + longText+ "_" + longText, ASCII_CONCAT_LENGTH);
        System.out.println("optimizedResult ASCII_CONCAT_LENGTH SIZE: "+optimizedResult.size());
        System.out.println("optimizedResult ASCII_CONCAT_LENGTH: "+toJSON(optimizedResult));

        // Assert that the results are the same
        assertEquals(25, optimizedResult.size(), "The split results for ASCII_CONCAT_LENGTH should be 25\n" +
                "for text size: " + longText.length());
    }


    @Test
    public void testSplitTestUNICODE_CONCAT_LENGTH() {
        // Define the maximum length of each split message

        // Get results from both methods
        List<String> optimizedResult2 = Utils.split(longText + "_" + longText+ "_" + longText, UNICODE_CONCAT_LENGTH);
        System.out.println("optimizedResult2 UNICODE_CONCAT_LENGTH SIZE: "+optimizedResult2.size());
        System.out.println("optimizedResult2 UNICODE_CONCAT_LENGTH: "+toJSON(optimizedResult2));

        // Assert that the results are the same
        assertEquals(64, optimizedResult2.size(), "The split results for UNICODE_CONCAT_LENGTH should be 64\n" +
                "for text size: " + longText.length());
    }


    @Test
    public void testSplitMethodsCompare() {
//        String longText = "START LONG TEXT {{current_date}} \\n1: Eng\\nabcdefghijklmnopqrstuvwxyz\\n2: Eng Caps\\naAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ\\n3: Hebrew\\nאבבּגדהוזחטיכךכּלמםנןסעפףפּצץקרששׁשׂת\\n4: Spanish\\n¡¿ÁÉÍÑÓÚÜáéíñóúü\\n5: Arabic\\nءآأؤإئابةتثجحخدذرزسشصضطظعغػؼؽؾؿـفقكلمنهوىي\\n6: Chinese\\n诶比西迪伊艾弗吉艾尺艾杰开艾勒艾马艾娜哦屁吉吾艾儿艾丝提伊吾维豆贝尔维艾克斯吾艾贼德\\n7: Japanese.1\\nあぁかさたなはまやゃらわがざだばぱいぃきしちにひみりゐぎじぢびぴうぅくすつぬふむゆゅるぐずづぶぷえ\\n7: Japanese.2\\nぇけせてねへめれゑげぜでべぺおぉこそとのほもよょろをごぞどぼぽゔっんーゝゞ、。\\n8: French\\nÀàÂâÆæÇçÉéÈèÊêËëÎîÏïÔôŒœÙùÛûÜüŸÿ«”“—»–’…·@¼½¾€\\n9: Russian.1\\nАаБбВвГгДдЕеЁёЖжЗзИиЙйКкЛлМмНнОоПпРрСсТтУуФфХх\\n9: Russian.2\\nЦцЧчШшЩщЪъЫыЬьЭэЮюЯяіІѣѢѳѲѵѴ\\n10: Currecny\\n$¢£€¥₹₽元¤₠₡₢₣₤₥₦₧₨₩₪₫₭₮₯₰₱₲₳₴₵₶₸₺₼₿৲৳૱௹฿៛㍐円圆圎圓圜원﷼＄￠￡￥￦\\n11: iso full\\nSP!\\\"#$%&'()*+,-.\\/0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~NBSP¡¢£¤¥¦§¨©ª«¬SHY®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏĞÑÒÓÔÕÖ×ØÙÚÛÜİŞßàáâãäåæçèéêëìíîïğñòóôõö÷øùúûüışÿ\\n12: Emojis.1\\n\uD83D\uDE03\uD83D\uDE04\uD83D\uDE01\uD83D\uDE06\uD83D\uDE05\uD83E\uDD23\uD83D\uDE02\uD83D\uDE42\uD83D\uDE43\uD83D\uDE09\uD83D\uDE0A\uD83D\uDE07\uD83E\uDD70\uD83D\uDE0D\uD83E\uDD29\uD83D\uDE18\uD83D\uDE17☺\uFE0F\uD83D\uDE1A\uD83D\uDE19\uD83D\uDE0B\uD83D\uDE1B\uD83D\uDE1C\uD83E\uDD2A\uD83D\uDE1D\uD83E\uDD11\uD83E\uDD17\uD83E\uDD2D\uD83E\uDD2B\uD83E\uDD14\uD83E\uDD10\uD83E\uDD28\uD83D\uDE10\\n12: Emojis.2\\n\uD83D\uDC7B\uD83D\uDC7D\uD83D\uDC7E\uD83E\uDD16\uD83D\uDE3A\uD83D\uDE38\uD83D\uDE39\uD83D\uDE3B\uD83D\uDE3C\uD83D\uDE3D\uD83D\uDE40\uD83D\uDE3F\uD83D\uDE3E\uD83D\uDE48\uD83D\uDE49\uD83D\uDE4A\uD83D\uDC8B\uD83D\uDC8C\uD83D\uDC98\uD83D\uDC9D\uD83D\uDC96\uD83D\uDC97\uD83D\uDC93\uD83D\uDC9E\uD83D\uDC95\uD83D\uDC9F❣\uFE0F\uD83D\uDC94❤\uFE0F\u200D\uD83D\uDD25❤\uFE0F\u200D\uD83E\uDE79\\nEND LONG TEXT. Sent at {{current_date}}";
        String longText = "START LONG TEXT {{current_date}} \\n1: Eng\\nabcdefghijklmnopqrstuvwxyz\\n" +
                "2: Eng Caps\\naAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ\\n" +
                "3: Hebrew\\nאבבּגדהוזחטיכךכּלמםנןסעפףפּצץקרששׁשׂת\\n" +
                "4: Spanish\\n¡¿\\n5: Arabic\\nءسشصضطظعغػؼؽؾؿـفقكلمنهوىي\\n6: Chinese\\n克斯吾艾贼德\\" +
                "n7: Japanese.1\\nあぁかさたなはまやゃらわがざだばぱいぃき\\n" +
                "7: Japanese.2\\nぇけせてねへめれゑげぜでべぺおぉこそとの\\n8: French\\nÀàÂâÆæÇçÉéÈèÊêËëÎîÏïÔôŒœÙùÛûÜüŸÿ«”“—»–’…·@¼½¾€\\n9: Russian.1\\nАаБбВвГгДдЕ\\n9: Russian.2\\nЦцЧчШ\\n10: Currecny\\n$¢£€¥₹₽元¤₠₡₢₣₤₥₦₧₨₩₪₫₭₮₯₰₱₲₳₴₵₶₸₺₼₿৲৳૱௹฿៛㍐円圆圎圓圜원﷼＄￠￡￥￦\\n11: iso full\\nSP!\\\"#$%&'()*+,-.\\/0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~NBSP¡»¼½¾¿ÀÁÂÃÄ\\n12: Emojis.1\\n\uD83D\\n12:\nEND LONG TEXT. Sent at {{current_date}}";

        // Define the maximum length of each split message
        int maxLength = 153;

        // Get results from both methods
        List<String> optimizedResult = Utils.split(longText, maxLength);
        System.out.println("optimizedResultSIZE: "+optimizedResult.size());
        System.out.println("optimizedResult: "+toJSON(optimizedResult));
        List<String> originalResult = Utils.splitOld(longText, maxLength);
        System.out.println("originalResultSIZE: " + originalResult.size());
        System.out.println("originalResult: " + toJSON(originalResult));

        // Assert that the results are the same
        assertEquals(originalResult, optimizedResult, "The split results do not match!");
    }

    @Test
    @Disabled
    public void testSCGSM() {
        try {
            CombinedCharsetProvider provider = new CombinedCharsetProvider();
            Charset actualCharset = provider.charsetForName("CCGSM");
            System.out.println("CombinedCharsetProvider SCGSM charset found: " + actualCharset.name());
        } catch (Exception e) {
            System.out.println("CombinedCharsetProvider SCGSM charset not found: " + e.getMessage());
        }
        try {
            Charset scgsmCharset = Charset.forName("SCGSM");
            System.out.println("SCGSM charset found: " + scgsmCharset.name());
        } catch (Exception e) {
            System.out.println("SCGSM charset not found: " + e.getMessage());
        }
        String text = "send \\n test with at the  test with at the  test with at the  test with at the  test with at the  test with at the  test with at the  test with at the  test with \\n at the  test with at the  test with at the  test with at the . Sent at Fri Jun 20 12:35:15 IDT 2025";
        Pair<Byte, String> dataCodingAndEnc = SimUtils
                .prepareDataCodingAndEnc(false, "SCGSM", (byte) 0, text);
        System.out.println(dataCodingAndEnc.toString());
        System.out.println("L:" +dataCodingAndEnc.getLeft());
        System.out.println("R:" +dataCodingAndEnc.getRight());
        String resText="";
        try {
            resText = new String(text.getBytes(), dataCodingAndEnc.getRight());
        } catch (UnsupportedEncodingException e) {
            fail("UnsupportedEncodingException should be supported");
        }

        System.out.println("resText:" + resText);
    }
    @Test
    @Disabled
    public void test() {

        String message = "START LONG TEXT {{current_date}} \\n1: Eng\\nabcdefghijklmnopqrstuvwxyz\\n2: Eng Caps\\naAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ\\n3: Hebrew\\nאבבּגדהוזחטיכךכּלמםנןסעפףפּצץקרששׁשׂת\\n4: Spanish\\n¡¿ÁÉÍÑÓÚÜáéíñóúü\\n5: Arabic\\nءآأؤإئابةتثجحخدذرزسشصضطظعغػؼؽؾؿـفقكلمنهوىي\\n6: Chinese\\n诶比西迪伊艾弗吉艾尺艾杰开艾勒艾马艾娜哦屁吉吾艾儿艾丝提伊吾维豆贝尔维艾克斯吾艾贼德\\n7: Japanese.1\\nあぁかさたなはまやゃらわがざだばぱいぃきしちにひみりゐぎじぢびぴうぅくすつぬふむゆゅるぐずづぶぷえ\\n7: Japanese.2\\nぇけせてねへめれゑげぜでべぺおぉこそとのほもよょろをごぞどぼぽゔっんーゝゞ、。\\n8: French\\nÀàÂâÆæÇçÉéÈèÊêËëÎîÏïÔôŒœÙùÛûÜüŸÿ«”“—»–’…·@¼½¾€\\n9: Russian.1\\nАаБбВвГгДдЕеЁёЖжЗзИиЙйКкЛлМмНнОоПпРрСсТтУуФфХх\\n9: Russian.2\\nЦцЧчШшЩщЪъЫыЬьЭэЮюЯяіІѣѢѳѲѵѴ\\n10: Currecny\\n$¢£€¥₹₽元¤₠₡₢₣₤₥₦₧₨₩₪₫₭₮₯₰₱₲₳₴₵₶₸₺₼₿৲৳૱௹฿៛㍐円圆圎圓圜원﷼＄￠￡￥￦\\n11: iso full\\nSP!\\\"#$%&'()*+,-.\\/0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~NBSP¡¢£¤¥¦§¨©ª«¬SHY®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏĞÑÒÓÔÕÖ×ØÙÚÛÜİŞßàáâãäåæçèéêëìíîïğñòóôõö÷øùúûüışÿ\\n12: Emojis.1\\n\uD83D\uDE03\uD83D\uDE04\uD83D\uDE01\uD83D\uDE06\uD83D\uDE05\uD83E\uDD23\uD83D\uDE02\uD83D\uDE42\uD83D\uDE43\uD83D\uDE09\uD83D\uDE0A\uD83D\uDE07\uD83E\uDD70\uD83D\uDE0D\uD83E\uDD29\uD83D\uDE18\uD83D\uDE17☺\uFE0F\uD83D\uDE1A\uD83D\uDE19\uD83D\uDE0B\uD83D\uDE1B\uD83D\uDE1C\uD83E\uDD2A\uD83D\uDE1D\uD83E\uDD11\uD83E\uDD17\uD83E\uDD2D\uD83E\uDD2B\uD83E\uDD14\uD83E\uDD10\uD83E\uDD28\uD83D\uDE10\\n12: Emojis.2\\n\uD83D\uDC7B\uD83D\uDC7D\uD83D\uDC7E\uD83E\uDD16\uD83D\uDE3A\uD83D\uDE38\uD83D\uDE39\uD83D\uDE3B\uD83D\uDE3C\uD83D\uDE3D\uD83D\uDE40\uD83D\uDE3F\uD83D\uDE3E\uD83D\uDE48\uD83D\uDE49\uD83D\uDE4A\uD83D\uDC8B\uD83D\uDC8C\uD83D\uDC98\uD83D\uDC9D\uD83D\uDC96\uD83D\uDC97\uD83D\uDC93\uD83D\uDC9E\uD83D\uDC95\uD83D\uDC9F❣\uFE0F\uD83D\uDC94❤\uFE0F\u200D\uD83D\uDD25❤\uFE0F\u200D\uD83E\uDE79\\nEND LONG TEXT. Sent at {{current_date}}";
        boolean isConvertToUnicode = false;
        try {
            isConvertToUnicode = isConvertToUnicode(message, "GSM7");
            System.out.println("res: " + isConvertToUnicode);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        if (message.length() <= (isConvertToUnicode ? MAX_UNICODE_CONCAT_LENGTH : MAX_ASCII_CONCAT_LENGTH)) {
            System.out.println("PAYLOAD/PAYLOAD_MESSAGE " + message);
        } else {
            int length = isConvertToUnicode ? UNICODE_CONCAT_LENGTH : ASCII_CONCAT_LENGTH;
            List<String> messages = Utils.splitByLength(message, length);
            System.out.println("split UDHI_PAYLOAD/UDHI/SAR {COUNT}" + messages.size());
            System.out.println("split UDHI_PAYLOAD/UDHI/SAR {}" + toJSON(messages));
            messages = Utils.split(message, length);
            System.out.println("split {}" + toJSON(messages));
            System.out.println("split {COUNT}" + messages.size());
        }
    }

    private static Path MessageFile_PATH = Paths.get(System.getProperty("user.dir"))
            .resolve("shared").resolve("sim").resolve("tmp");

    @Test
    @Disabled
    public void testPerfSearchTestGenData() throws IOException {
        cacheService = new MessagesCache(null);
        cacheService.generateTestData(10000, MessageFile_PATH.resolve("test.json"));
    }

    @Test
    @Disabled
    public void testPerfSearchTest() throws IOException {
        // Test method to compare performance of parallelStream vs stream
        List<String> finds = List.of("1563043424", "zu1OXCoZho20PB", "nSpRq1oE6", "zrHJAM5weSUAfakTMwk",
                "4c87fd6a-3d5e-441c-a11c-ff62c1220cc3", "08:22:34 IST ") ;

                cacheService = new MessagesCache(null);
//            Collection<MessagesObject> dataList = cacheService.getMap().values();
        log.info("{} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} ");
        log.info("{} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} {} ");
        finds.forEach(find -> {
            log.info("TEST Text: {} ", find);
            long startTime = System.nanoTime();
            List<MessagesObject> sequentialResults = cacheService.getMessagesByText(find);
            long endTime = System.nanoTime();
            long sequentialTime = endTime - startTime;
            log.info("  Parallel Stream Execution Time: {} ms", sequentialTime / 1_000_000);

            startTime = System.nanoTime();
            List<MessagesObject> byId = cacheService.getMessagesByText(find);
            endTime = System.nanoTime();
            long idTime = endTime - startTime;
            log.info("  Parallel Stream Execution Time: {} ms", idTime / 1_000_000);
            log.info("  ById Results Size: {}, Parallel Results Size: {}", sequentialResults.size(), byId.size());
        });

        }


    @Test
    @Disabled
    public void pduTest() throws IOException {
        String input2 = ".... (submit_resp: (pdu: 0 80000004 0 25604) 4521885643 4521885643 ) ....";
        String input = "(submit_resp: (pdu: 0 80000004 0 25604) 4521885643 4521885643 ) \n" +
                "DR: DELIVRD\n" +
                "\n" +
                "sent response=falserequest:{\"dst\":\"Getty\",\"src\":\"972526549318\",\"text\":\"id:4521885643 sub:000 dlvrd:000 submit date:1748161933365 done date:0911220855 stat:DELIVRD err:000 text:\"}\n" +
                "(submit_resp: (pdu: 0 80000004 0 25604) 4521885643 4521885643 ) \n" +
                "DR: DELIVRD\n" +
                "\n" +
                "sent response=falserequest:{\"dst\":\"Getty\",\"src\":\"972526549318\",\"text\":\"id:4521885643 sub:000 dlvrd:000 submit date:1748161933365 done date:0911220855 stat:DELIVRD err:000 text:\"}";
        // Define the regex pattern
        String regex = "\\(submit_resp: \\(pdu: [^)]*\\) (\\d+) \\d+ \\)";

        // Compile the pattern
        Pattern pattern = Pattern.compile(regex);
        // Match against the input string
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            // Group 1 contains the first instance of the number
            String result = matcher.group(1);
            System.out.println("Extracted number: " + result);
        } else {
            System.out.println("No match found.");
        }
    }
}
