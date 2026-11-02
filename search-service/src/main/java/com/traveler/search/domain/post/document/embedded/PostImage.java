package com.traveler.search.domain.post.document.embedded;

import lombok.*;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostImage {

    @Field(type = FieldType.Keyword)
    private String imageKey;

    @Field(type = FieldType.Integer)
    private Integer sortOrder;
}
