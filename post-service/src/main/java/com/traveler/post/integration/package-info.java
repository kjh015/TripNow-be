/**
 * 다른 서비스와 이벤트를 주고받는 코드. 하위 패키지 이름은 상대 서비스다(member = member-service, useractivity =
 * user-activity-service).
 *
 * <p>상대 서비스의 메시지를 받아 이 서비스의 도메인(domain/**)을 호출하고, 필요하면 결과를 돌려 발행한다. 여러 도메인을 묶는 흐름도
 * 여기에 둔다. domain은 integration을 참조하지 않는다.
 */
package com.traveler.post.integration;
