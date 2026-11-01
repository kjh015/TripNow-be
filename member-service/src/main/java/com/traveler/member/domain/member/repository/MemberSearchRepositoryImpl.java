package com.traveler.member.domain.member.repository;

import static com.traveler.member.domain.member.entity.QMember.member;
import static com.traveler.member.domain.member.entity.QMemberRole.memberRole;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.traveler.member.domain.member.dto.request.AdminMemberRequest;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.Provider;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class MemberSearchRepositoryImpl implements MemberSearchRepository {
    // 관리자가 입력하는 가입일은 한국 날짜로 보고, 그날 0시부터 다음 날 0시 전까지로 바꾼다
    private static final ZoneId JOINED_DATE_ZONE = ZoneId.of("Asia/Seoul");

    private static final LocalDate MIN_QUERYABLE_DATE = LocalDate.of(1000, 1, 2);
    private static final LocalDate MAX_QUERYABLE_DATE = LocalDate.of(9999, 12, 30);
    private static final Map<String, ComparableExpressionBase<?>> SORTABLE_FIELDS = Map.of(
            "createdAt", member.createdAt,
            "loginId", member.loginId,
            "nickname", member.nickname,
            "email", member.email);

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Member> search(AdminMemberRequest.SearchDTO condition, Pageable pageable) {
        BooleanExpression[] conditions = {
            keywordStartsWith(condition.keyword()),
            providerEq(condition.provider()),
            hasRole(condition.role()),
            deletedEq(condition.deleted()),
            joinedFrom(condition.joinedFrom()),
            joinedTo(condition.joinedTo())
        };

        List<Member> content = queryFactory
                .selectFrom(member)
                .where(conditions)
                .orderBy(toOrderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery =
                queryFactory.select(member.count()).from(member).where(conditions);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression keywordStartsWith(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String prefix = keyword.strip();
        return member.loginId
                .startsWith(prefix)
                .or(member.nickname.startsWith(prefix))
                .or(member.email.startsWith(prefix));
    }

    private BooleanExpression providerEq(Provider provider) {
        return provider == null ? null : member.provider.eq(provider);
    }

    private BooleanExpression hasRole(RoleType role) {
        if (role == null) {
            return null;
        }
        return JPAExpressions.selectOne()
                .from(memberRole)
                .where(memberRole.member.eq(member), memberRole.roleType.eq(role))
                .exists();
    }

    private BooleanExpression deletedEq(Boolean deleted) {
        return deleted == null ? null : member.isDeleted.eq(deleted);
    }

    private BooleanExpression joinedFrom(LocalDate from) {
        return from == null ? null : member.createdAt.goe(startOfDay(toQueryableDate(from)));
    }

    private BooleanExpression joinedTo(LocalDate to) {
        return to == null
                ? null
                : member.createdAt.lt(startOfDay(toQueryableDate(to).plusDays(1)));
    }

    // MySQL DATETIME 범위 밖의 값은 쿼리에서 오류가 난다. 가입일은 이 범위 안에 있어 끝값으로 맞춰도 결과가 같다
    private LocalDate toQueryableDate(LocalDate date) {
        if (date.isBefore(MIN_QUERYABLE_DATE)) {
            return MIN_QUERYABLE_DATE;
        }
        return date.isAfter(MAX_QUERYABLE_DATE) ? MAX_QUERYABLE_DATE : date;
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(JOINED_DATE_ZONE).toInstant();
    }

    // 요청의 정렬 필드는 허용 목록을 거쳐서만 쿼리에 넣는다. 같은 값이 많아도 페이지 경계가 흔들리지 않게 id를 마지막 기준으로 둔다
    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            ComparableExpressionBase<?> path = SORTABLE_FIELDS.get(order.getProperty());
            if (path == null) {
                throw new MemberServiceException(MemberServiceErrorCode.MEMBER_INVALID_SORT);
            }
            orders.add(order.isAscending() ? path.asc() : path.desc());
        }
        orders.add(new OrderSpecifier<>(Order.DESC, member.id));
        return orders.toArray(OrderSpecifier[]::new);
    }
}
