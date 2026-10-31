package com.traveler.post.integration.member.mapper;

import com.traveler.post.integration.member.dto.event.MemberEvent;
import com.traveler.post.integration.member.dto.message.MemberMessage;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MemberMapper {
    // Event
    default MemberEvent.ContentDeleted toContentDeletedEvent(
            Long memberId, Instant deletedAt, int posts, int comments, int likes) {
        return new MemberEvent.ContentDeleted(
                new MemberMessage.ContentDeletedDTO(memberId, deletedAt, posts, comments, likes));
    }
}
