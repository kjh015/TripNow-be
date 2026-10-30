package com.traveler.member.global.outbox.mapper;

import com.traveler.member.global.outbox.entity.Outbox;
import com.traveler.member.global.outbox.event.OutboxEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OutboxMapper {
    @Mapping(target = "payload", source = "jsonPayload")
    @Mapping(target = "eventId", source = "event.eventId")
    @Mapping(target = "status", constant = "INIT")
    Outbox toEntity(OutboxEvent event, String jsonPayload);
}
