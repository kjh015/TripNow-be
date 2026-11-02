package com.traveler.useractivity.domain.history.document.embedded;

import lombok.*;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HistoryFailInfo {

    @Field(type = FieldType.Keyword)
    private String code;

    @Field(type = FieldType.Keyword)
    private String stage;

    @Field(name = "fail_rule_id", type = FieldType.Keyword)
    private Long failRuleId;

    @Field(name = "fail_rule_name", type = FieldType.Keyword)
    private String failRuleName;

    @Field(type = FieldType.Keyword)
    private String detail;
}
