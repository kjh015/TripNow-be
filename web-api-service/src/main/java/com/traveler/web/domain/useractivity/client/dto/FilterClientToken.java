package com.traveler.web.domain.useractivity.client.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.traveler.web.domain.useractivity.enums.ComparisonOperator;
import com.traveler.web.domain.useractivity.enums.LogicalOperator;
import com.traveler.web.domain.useractivity.enums.ValueType;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = FilterClientToken.Condition.class, name = "condition"),
    @JsonSubTypes.Type(value = FilterClientToken.Operator.class, name = "operator"),
    @JsonSubTypes.Type(value = FilterClientToken.Paren.class, name = "left-paren"),
    @JsonSubTypes.Type(value = FilterClientToken.Paren.class, name = "right-paren")
})
public sealed interface FilterClientToken {
    String type();

    Long groupId();

    record Condition(
            String type, Long groupId, String field, ComparisonOperator operator, String value, ValueType valueType)
            implements FilterClientToken {}

    record Operator(String type, Long groupId, LogicalOperator value) implements FilterClientToken {}

    record Paren(String type, Long groupId) implements FilterClientToken {}
}
