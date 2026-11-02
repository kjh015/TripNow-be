package com.traveler.search.domain.post.document;

import com.traveler.search.domain.post.document.embedded.PostImage;
import com.traveler.search.global.document.BaseDocument;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.elasticsearch.annotations.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SuperBuilder
@Document(indexName = "#{@environment.getProperty('app.elasticsearch.indices.post')}")
@Setting(settingPath = "/elasticsearch/post-settings.json")
@Mapping(mappingPath = "/elasticsearch/post-mappings.json")
public class PostDocument extends BaseDocument {

    public static final class Fields {
        public static final String ID = "id";
        public static final String CREATED_AT = "createdAt";
        public static final String MEMBER_ID = "memberId";
        public static final String TITLE = "title";
        public static final String TITLE_AUTOCOMPLETE = "title.autocomplete";
        public static final String TITLE_NGRAM = "title.ngram";
        public static final String TITLE_CHOSUNG = "titleChosung";
        public static final String CONTENT = "content";
        public static final String TRAVEL_PLACE = "travelPlace";
        public static final String ADDRESS = "address";
        public static final String CATEGORY = "category";
        public static final String REGION = "region";
        public static final String POPULARITY_SCORE = "popularityScore";
        public static final String POPULARITY_SCORE_VALUE = "popularityScoreValue";
        public static final String IMAGES = "images";
    }

    @Field(type = FieldType.Long)
    private Long memberId;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "korean_nori"),
            otherFields = {
                @InnerField(suffix = "ngram", type = FieldType.Text, analyzer = "ngram_no_space_analyzer"),
                @InnerField(suffix = "autocomplete", type = FieldType.Text, analyzer = "edge_ngram_no_space_analyzer")
            })
    private String title;

    @Field(type = FieldType.Text, analyzer = "chosung_ngram_analyzer")
    private String titleChosung;

    @Field(type = FieldType.Text, analyzer = "korean_nori")
    private String content;

    @Field(type = FieldType.Text, analyzer = "korean_nori")
    private String travelPlace;

    @Field(type = FieldType.Text, analyzer = "korean_nori")
    private String address;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String region;

    @Field(type = FieldType.Double)
    @Builder.Default
    private Double starAvg = 0.0;

    @Field(type = FieldType.Long)
    @Builder.Default
    private Long viewCount = 0L;

    @Field(type = FieldType.Long)
    @Builder.Default
    private Long likeCount = 0L;

    @Field(type = FieldType.Long)
    @Builder.Default
    private Long commentCount = 0L;

    @Field(type = FieldType.Rank_Feature)
    private Double popularityScore;

    // popularityScore(rank_feature)는 relevance boost 전용이라 정렬/집계를 지원하지 않으므로,
    // 정렬·집계용으로 동일한 값을 별도의 숫자 필드에 복제해서 저장한다.
    @Field(type = FieldType.Double)
    @Builder.Default
    private Double popularityScoreValue = 0.0;

    @Field(type = FieldType.Nested)
    private List<PostImage> images;
}
