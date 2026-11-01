package com.traveler.member.domain.member.repository;

import com.traveler.member.domain.member.dto.request.AdminMemberRequest;
import com.traveler.member.domain.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberSearchRepository {
    Page<Member> search(AdminMemberRequest.SearchDTO condition, Pageable pageable);
}
