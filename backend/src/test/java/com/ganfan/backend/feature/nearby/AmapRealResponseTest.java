package com.ganfan.backend.feature.nearby;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganfan.backend.feature.nearby.VenueProvider.Venue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 基于高德真实响应结构的回归测试。
 *
 * <p>数据由真实接口抓取（2026-11-02，屏峰校区周边餐饮，extensions=all），
 * 覆盖真实响应里那些文档未明确说明的形态：
 * <ul>
 *   <li>{@code biz_ext.cost} 缺失时是空数组 {@code []}，存在时是字符串 {@code "14.00"}</li>
 *   <li>{@code biz_ext.rating} 是字符串</li>
 *   <li>{@code tel} / {@code tag} 缺失时是空数组 {@code []}</li>
 *   <li>{@code photos} 元素结构为 {@code {title, url}}，URL 混用 http 与 https</li>
 * </ul>
 */
class AmapRealResponseTest {
    /** 真实响应节选：cost 为空数组、tel 为空数组、tag 为字符串、含 http 图片。 */
    private static final String REAL_AMAP = """
            {
              "status": "1",
              "info": "OK",
              "count": "3",
              "pois": [
                {
                  "id": "B0L0RC62XK",
                  "name": "奕鸣煲仔饭(工大家和堂店)",
                  "type": "餐饮服务;中餐厅;中餐厅",
                  "typecode": "050100",
                  "address": "浙江工业大学屏峰校区家和西苑",
                  "location": "120.041125,30.228375",
                  "tel": [],
                  "tag": "煲仔饭",
                  "distance": "38",
                  "business_area": "小和山",
                  "biz_ext": { "cost": [], "rating": "2.9" },
                  "photos": []
                },
                {
                  "id": "B0MAMX897Z",
                  "name": "吉祥馄饨(家和堂店)",
                  "type": "餐饮服务;中餐厅;中餐厅",
                  "typecode": "050100",
                  "keytag": "小吃快餐",
                  "address": "留下街道留和路288号浙江工业大学(屏峰校区)",
                  "location": "120.041075,30.229025",
                  "tel": [],
                  "tag": [],
                  "distance": "39",
                  "business_area": "小和山",
                  "biz_ext": { "cost": [], "rating": [] },
                  "photos": []
                },
                {
                  "id": "B0K2H4K68B",
                  "name": "浙工大屏峰校区校内餐厅",
                  "type": "餐饮服务;中餐厅;中餐厅",
                  "typecode": "050100",
                  "address": "留和路与和林路交叉口北420米",
                  "location": "120.040765,30.228970",
                  "tel": [],
                  "tag": [],
                  "distance": "45",
                  "business_area": "小和山",
                  "biz_ext": { "cost": "33.50", "rating": "4.2" },
                  "photos": [
                    { "title": [], "url": "http://aos-comment.amap.com/B0K2H4K68B/photo.jpg" }
                  ]
                }
              ]
            }
            """;

    private List<Venue> parse() {
        AmapVenueProvider provider = new AmapVenueProvider(
                new ObjectMapper(), "test-key", "", "050000", 25,
                "http://127.0.0.1:1/unused", 300, 4);
        return provider.parse(REAL_AMAP);
    }

    @Test
    void emptyArrayCostIsTreatedAsUnknownPrice() {
        Venue first = parse().get(0);
        assertThat(first.price()).isZero();
        assertThat(first.rating()).isEqualTo(2.9);
    }

    @Test
    void emptyArrayRatingIsTreatedAsUnknown() {
        Venue second = parse().get(1);
        assertThat(second.rating()).isZero();
        assertThat(second.price()).isZero();
    }

    @Test
    void decimalCostIsRoundedNotTruncated() {
        Venue third = parse().get(2);
        // "33.50" 若用 asInt 会被截断成 33，这里必须是四舍五入的 34。
        assertThat(third.price()).isEqualTo(34);
        assertThat(third.rating()).isEqualTo(4.2);
    }

    @Test
    void httpPhotoUrlIsAccepted() {
        Venue third = parse().get(2);
        assertThat(third.img()).isEqualTo("http://aos-comment.amap.com/B0K2H4K68B/photo.jpg");
    }

    @Test
    void missingTelAndTagDoNotBreakMapping() {
        Venue first = parse().get(0);
        assertThat(first.attributes().get("tel")).isEqualTo("");
        // tag 是字符串 "煲仔饭"，应被识别为特色菜并用于挑选表情。
        assertThat(first.dishes()).containsExactly("煲仔饭");
        assertThat(first.emoji()).isEqualTo("🍲");

        Venue second = parse().get(1);
        assertThat(second.dishes()).containsExactly("小吃快餐");
        assertThat(second.emoji()).isEqualTo("🍱");
    }

    @Test
    void duplicatedCategoryLevelsAreDeduplicated() {
        // 高德 type 为 "餐饮服务;中餐厅;中餐厅"，去重后不应出现重复标签。
        Venue first = parse().get(0);
        assertThat(first.tags()).containsExactly("中餐厅", "小和山");
        assertThat(first.tags()).doesNotHaveDuplicates();
    }

    @Test
    void realPoilocationsAreConvertedToWgs84() {
        Venue third = parse().get(2);
        // GCJ-02 (120.040765, 30.228970) -> WGS-84 (120.0359989, 30.2313270)
        assertThat(third.lng()).isCloseTo(120.0359989, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(third.lat()).isCloseTo(30.2313270, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void businessAreaBecomesTagAndCanteen() {
        Venue first = parse().get(0);
        assertThat(first.tags()).contains("小和山");
        assertThat(first.attributes().get("businessArea")).isEqualTo("小和山");
        assertThat(first.attributes().get("canteen")).isEqualTo("小和山");
    }
}
