// BaseEntity는 common:db 모듈에 있어 Q 클래스가 저절로 만들어지지 않는다. QMember가 상속 필드를 쓰도록 여기서 생성을 지정한다
@QueryEntities(BaseEntity.class)
package com.traveler.member.global.config;

import com.querydsl.core.annotations.QueryEntities;
import com.traveler.common.db.entity.BaseEntity;
