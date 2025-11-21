package com.telemessage.simulators.controllers.message;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for returning rich message details 
 * including binary data and encoding information
 */
@Data
@NoArgsConstructor
public class MessageDetailsDTO {
    private MessagesObject message;
    private boolean hasBinaryData;
    private String encoding;
    private int binaryDataSize;

    String s = "Test:LongTXT_ArchiverTo1Temp_MMS_OUT START:<br/>\n" +
            "START LONG TEXT<br/>\n" +
            "1: Emojis.1<br/>\n" +
            "\uD83D\uDE03\uD83D\uDE04\uD83D\uDE01\uD83D\uDE06\uD83D\uDE05\uD83E\uDD23\uD83D\uDE02\uD83D\uDE42\uD83D\uDE43\uD83D\uDE09\uD83D\uDE0A\uD83D\uDE07\uD83E\uDD70\uD83D\uDE0D\uD83E\uDD29\uD83D\uDE18\uD83D\uDE17<br/>\n" +
            " ☺\uFE0F\uD83D\uDE1A\uD83D\uDE19\uD83D\uDE0B\uD83D\uDE1B\uD83D\uDE1C\uD83E\uDD2A\uD83D\uDE1D\uD83E\uDD11\uD83E\uDD17\uD83E\uDD2D\uD83E\uDD2B\uD83E\uDD14\uD83E\uDD10\uD83E\uDD28\uD83D\uDE10<br/>\n" +
            "1: Emojis.2<br/>\n" +
            "\uD83D\uDC7B\uD83D\uDC7D\uD83D\uDC7E\uD83E\uDD16\uD83D\uDE3A\uD83D\uDE38\uD83D\uDE39\uD83D\uDE3B\uD83D\uDE3C\uD83D\uDE3D\uD83D\uDE40\uD83D\uDE3F\uD83D\uDE3E\uD83D\uDE48\uD83D\uDE49\uD83D\uDE4A<br/>\n" +
            "  \uD83D\uDC8B\uD83D\uDC8C\uD83D\uDC98\uD83D\uDC9D\uD83D\uDC96\uD83D\uDC97\uD83D\uDC93\uD83D\uDC9E\uD83D\uDC95\uD83D\uDC9F❣\uFE0F\uD83D\uDC94❤\uFE0F\u200D\uD83D\uDD25❤\uFE0F\u200D\uD83E\uDE79<br/>\n" +
            "2: Eng<br/>\n" +
            "abcdefghijklmnopqrstuvwxyz<br/>\n" +
            "3: Eng Caps<br/>\n" +
            "aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ<br/>\n" +
            "4: Hebrew<br/>\n" +
            "אבבּגדהוזחטיכךכּלמםנןסעפףפּצץקרששׁשׂת<br/>\n" +
            "5: Spanish<br/>\n" +
            "¡¿ÁÉÍÑÓÚÜáéíñóúü<br/>\n" +
            "6: Arabic<br/>\n" +
            "ءآأؤإئابةتثجحخدذرزسشصضطظعغػؼؽؾؿـفقكلمنهوىي<br/>\n" +
            "7:Hindi<br/>\n" +
            "अ आ इ ई उ ऊ ऋ ॠ ए ऐ ओ औ अं अः क ख ग घ ङ च छ ज झ ञ ट ठ ड ढ ण त थ द ध न प फ ब भ म य र ल व श ष स ह क्ष त्र ज्ञ श्र ळ क़ ख़ ग़ ज़ ड़ ढ़ फ़ य़ क़ ख़ ग़ ज़ ड़ ढ़ फ़ य़ ॐ ड़ ज़़ ड़़ य़़<br/>\n" +
            "8: Chinese<br/>\n" +
            "诶比西迪伊艾弗吉艾尺艾杰开艾勒艾马艾娜哦屁吉吾艾儿艾丝提伊吾维豆贝尔维艾克斯吾艾贼德<br/>\n" +
            "9: Japanese.1<br/>\n" +
            "あぁかさたなはまやゃらわがざだばぱいぃきしちにひみりゐぎじぢびぴうぅくすつぬふむゆゅるぐずづぶぷえ<br/>\n" +
            "9: Japanese.2<br/>\n" +
            "ぇけせてねへめれゑげぜでべぺおぉこそとのほもよょろをごぞどぼぽゔっんーゝゞ、。<br/>\n" +
            "10: French<br/>\n" +
            "ÀàÂâÆæÇçÉéÈèÊêËëÎîÏïÔôŒœÙùÛûÜüŸÿ«”“—»–’…·@¼½¾€<br/>\n" +
            "11: Russian.1<br/>\n" +
            "АаБбВвГгДдЕеЁёЖжЗзИиЙйКкЛлМмНнОоПпРрСсТтУуФфХх<br/>\n" +
            "11: Russian.2<br/>\n" +
            "ЦцЧчШшЩщЪъЫыЬьЭэЮюЯяіІѣѢѳѲѵѴ<br/>\n" +
            "12: Currecny<br/>\n" +
            "$¢£€¥₹₽元¤₠₡₢₣₤₥₦₧₨₩₪₫₭₮₯₰₱₲₳₴₵₶₸₺₼₿৲৳૱௹฿៛㍐円圆圎圓圜원﷼＄￠￡￥￦<br/>\n" +
            "13: iso full<br/>\n" +
            "SP!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~NBSP¡¢£¤¥¦§¨©ª«¬SHY®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏĞÑÒÓÔÕÖ×ØÙÚÛÜİŞßàáâãäåæçèéêëìíîïğñòóôõö÷øùúûüışÿ<br/>\n" +
            "END LONG TEXT\n";
}
