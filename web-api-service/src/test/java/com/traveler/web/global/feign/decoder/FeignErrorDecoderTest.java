package com.traveler.web.global.feign.decoder;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.common.core.code.BaseErrorCode;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.exception.code.WebApiServiceErrorCode;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder(new ObjectMapper());

    @Test
    @DisplayName("하위 서비스 4xx 응답은 상태·코드·메시지를 그대로 담은 예외로 바꾼다")
    void clientErrorKeepsDownstreamCode() {
        Exception ex = decoder.decode(
                "MemberClient#getMemberProfile(Long)",
                response(404, "{\"success\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원 없음\",\"result\":null}"));

        assertThat(code(ex)).isEqualTo(new ServiceErrorCode(404, "MEMBER404_1", "회원 없음"));
    }

    @Test
    @DisplayName("하위 서비스 5xx 응답도 상태를 유지해 재시도·fallback 판단에 쓰이게 한다")
    void serverErrorKeepsStatus() {
        Exception ex = decoder.decode(
                "PostClient#createPost",
                response(503, "{\"success\":false,\"code\":\"COMMON503\",\"message\":\"점검 중\",\"result\":null}"));

        assertThat(code(ex).getStatus()).isEqualTo(503);
        assertThat(code(ex).getCode()).isEqualTo("COMMON503");
    }

    @Test
    @DisplayName("코드·메시지가 비어 있으면 SERVICE{status}와 기본 메시지로 채운다")
    void blankCodeIsNormalized() {
        Exception ex = decoder.decode("PostClient#getPost", response(502, "{\"success\":false}"));

        assertThat(code(ex).getCode()).isEqualTo("SERVICE502");
        assertThat(code(ex).getMessage()).isEqualTo("하위 서비스에서 에러가 발생했습니다.");
    }

    @Test
    @DisplayName("응답 바디가 없으면 SERVICE_RESPONSE_EMPTY로 바꾼다")
    void emptyBody() {
        Exception ex = decoder.decode("PostClient#getPost", response(500, null));

        assertThat(code(ex)).isEqualTo(WebApiServiceErrorCode.SERVICE_RESPONSE_EMPTY);
    }

    @Test
    @DisplayName("ApiResponse 형식이 아닌 바디(예: 프록시 HTML 에러 페이지)는 SERVICE_PARSE_ERROR로 바꾼다")
    void unparsableBody() {
        Exception ex = decoder.decode("PostClient#getPost", response(502, "<html>Bad Gateway</html>"));

        assertThat(code(ex)).isEqualTo(WebApiServiceErrorCode.SERVICE_PARSE_ERROR);
    }

    private static BaseErrorCode code(Exception ex) {
        assertThat(ex).isInstanceOf(WebApiServiceException.class);
        return ((WebApiServiceException) ex).getCode();
    }

    private static Response response(int status, String body) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://member-service/v1/members/1",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        Response.Builder builder = Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Map.of());
        if (body != null) {
            builder.body(body, StandardCharsets.UTF_8);
        }
        return builder.build();
    }
}
