package com.traveler.web.domain.member.client.dto.request;

import com.traveler.web.domain.member.enums.Provider;
import com.traveler.web.domain.member.enums.RoleType;
import java.time.LocalDate;

public final class AdminMemberClientRequest {
    private AdminMemberClientRequest() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record SearchDTO(
            String keyword,
            Provider provider,
            RoleType role,
            Boolean deleted,
            LocalDate joinedFrom,
            LocalDate joinedTo) {}
}
