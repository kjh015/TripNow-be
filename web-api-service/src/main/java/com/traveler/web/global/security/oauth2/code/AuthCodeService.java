package com.traveler.web.global.security.oauth2.code;

import com.traveler.common.support.logging.mask.LogMasking;
import com.traveler.web.domain.member.client.dto.request.AuthClientRequest;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.exception.code.WebApiServiceErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCodeService {

    private static final int CODE_LOG_PREFIX_LENGTH = 8;

    private final AuthCodeRepository authCodeRepository;

    public String createAuthCode(String provider, String providerId, String email) {
        String code = UUID.randomUUID().toString();
        AuthClientRequest.OauthLoginDTO oauthLoginReq = AuthClientRequest.OauthLoginDTO.of(provider, providerId, email);

        authCodeRepository.save(code, oauthLoginReq);

        return code;
    }

    public AuthClientRequest.OauthLoginDTO verifyAndConsumeCode(String code) {
        return authCodeRepository.findAndDelete(code).orElseThrow(() -> {
            // 이미 소모됐거나 만료된 코드: 재사용 시도 신호
            log.warn("[OAuth2] 인가 코드 없음 또는 만료 code={}", LogMasking.prefix(code, CODE_LOG_PREFIX_LENGTH));
            return new WebApiServiceException(WebApiServiceErrorCode.INVALID_AUTH_CODE);
        });
    }

    // 장애 발생 시 지워진 코드 복구 메서드
    public void restoreCode(String code, AuthClientRequest.OauthLoginDTO dto) {
        log.warn(
                "[OAuth2] member-service 소셜 로그인 호출이 재시도 가능한 장애로 실패해 인가 코드 복구 code={}",
                LogMasking.prefix(code, CODE_LOG_PREFIX_LENGTH));
        authCodeRepository.save(code, dto);
    }
}
