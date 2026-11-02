package com.traveler.web.domain.useractivity.mapper;

import com.traveler.web.domain.useractivity.client.dto.FilterClientToken;
import com.traveler.web.domain.useractivity.client.dto.request.FilterRuleClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.FilterRuleClientResponse;
import com.traveler.web.domain.useractivity.dto.FilterToken;
import com.traveler.web.domain.useractivity.dto.request.FilterRuleRequest;
import com.traveler.web.domain.useractivity.dto.response.FilterRuleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FilterRuleMapper {
    FilterRuleClientRequest.CreateDTO toClientCreateDTO(FilterRuleRequest.CreateDTO dto);

    FilterRuleClientRequest.UpdateDTO toClientUpdateDTO(FilterRuleRequest.UpdateDTO dto);

    @SubclassMapping(source = FilterToken.Condition.class, target = FilterClientToken.Condition.class)
    @SubclassMapping(source = FilterToken.Operator.class, target = FilterClientToken.Operator.class)
    @SubclassMapping(source = FilterToken.Paren.class, target = FilterClientToken.Paren.class)
    FilterClientToken toClientToken(FilterToken token);

    FilterClientToken.Condition toClientCondition(FilterToken.Condition condition);

    FilterClientToken.Operator toClientOperator(FilterToken.Operator operator);

    FilterClientToken.Paren toClientParen(FilterToken.Paren paren);

    FilterRuleResponse.CreateDTO toResponseCreateDTO(FilterRuleClientResponse.CreateDTO clientResponse);

    FilterRuleResponse.UpdateDTO toResponseUpdateDTO(FilterRuleClientResponse.UpdateDTO clientResponse);

    FilterRuleResponse.DeleteDTO toResponseDeleteDTO(FilterRuleClientResponse.DeleteDTO clientResponse);

    FilterRuleResponse.ListDTO toResponseListDTO(FilterRuleClientResponse.ListDTO listDTO);

    FilterRuleResponse.DetailDTO toResponseDetailDTO(FilterRuleClientResponse.DetailDTO clientResponse);

    @SubclassMapping(source = FilterClientToken.Condition.class, target = FilterToken.Condition.class)
    @SubclassMapping(source = FilterClientToken.Operator.class, target = FilterToken.Operator.class)
    @SubclassMapping(source = FilterClientToken.Paren.class, target = FilterToken.Paren.class)
    FilterToken toResponseToken(FilterClientToken token);

    FilterToken.Condition toResponseCondition(FilterClientToken.Condition condition);

    FilterToken.Operator toResponseOperator(FilterClientToken.Operator operator);

    FilterToken.Paren toResponseParen(FilterClientToken.Paren paren);
}
