// 요청에 쓰는 값 목록. 검색 키워드는 시드 게시글(seed-posts.js)의 제목·본문·장소에 들어가는 단어와 맞춘다.

export const KEYWORDS = ['맛집', '카페', '바다', '야경', '산책', '축제', '시장', '전시', '등산', '온천'];

export const PLACES = [
    { name: '수원화성', address: '경기도 수원시 팔달구 정조로', region: 'GYEONGGI' },
    { name: '해운대', address: '부산광역시 해운대구 우동', region: 'BUSAN' },
    { name: '성산일출봉', address: '제주특별자치도 서귀포시 성산읍', region: 'JEJU' },
    { name: '북촌한옥마을', address: '서울특별시 종로구 계동', region: 'SEOUL' },
    { name: '경포해변', address: '강원특별자치도 강릉시 창해로', region: 'GANGWON' },
    { name: '수성못', address: '대구광역시 수성구 두산동', region: 'DAEGU' },
    { name: '월미도', address: '인천광역시 중구 북성동', region: 'INCHEON' },
    { name: '순천만습지', address: '전라남도 순천시 순천만길', region: 'JEONNAM' },
];

// PostCategory, PostRegion, PostSortType (web-api-service enums)
export const CATEGORIES = ['FESTIVAL', 'PERFORMANCE', 'EVENT', 'EXPERIENCE', 'SHOPPING', 'NATURE', 'HISTORY', 'FAMILY', 'FOOD', 'ETC'];
export const REGIONS = ['GANGWON', 'GYEONGGI', 'DAEGU', 'BUSAN', 'SEOUL', 'INCHEON', 'JEONNAM', 'JEJU'];
export const SORTS = ['ACCURACY', 'POPULAR', 'LATEST', 'STAR_AVG', 'VIEW_COUNT'];

export function pick(list) {
    return list[Math.floor(Math.random() * list.length)];
}

/** 검색 조건 조합: 키워드·카테고리·지역은 각각 절반 확률로 넣고, 정렬은 항상 넣는다 */
export function randomSearchParams() {
    return {
        keyword: Math.random() < 0.5 ? pick(KEYWORDS) : undefined,
        category: Math.random() < 0.5 ? pick(CATEGORIES) : undefined,
        region: Math.random() < 0.5 ? pick(REGIONS) : undefined,
        sort: pick(SORTS),
        page: Math.floor(Math.random() * 3),
        size: 10,
    };
}
