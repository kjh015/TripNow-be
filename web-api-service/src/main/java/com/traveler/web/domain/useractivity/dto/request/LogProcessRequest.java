package com.traveler.web.domain.useractivity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class LogProcessRequest {
    // 이름은 로그 파이프라인의 프로세스 코드로 쓰이므로 user-activity-service와 같은 형식만 허용
    private static final String CODE_REGEX = "^[A-Za-z][A-Za-z0-9]*$";
    private static final String CODE_MESSAGE = "로그 프로세스 이름은 영문자로 시작하는 영문자·숫자 조합이어야 합니다. (예: View, ListClick)";

    private LogProcessRequest() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @Schema(name = "LogProcessCreateRequest")
    public record CreateDTO(
            @NotBlank(message = "로그 프로세스 이름은 필수입니다.")
                    @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE)
                    @Schema(description = "로그 프로세스 이름 (파이프라인 프로세스 코드, 영문자로 시작하는 영숫자)", example = "Payment")
                    String name,
            @Size(max = 1000, message = "설명은 1000자를 초과할 수 없습니다.")
                    @Schema(description = "로그 프로세스 설명", example = "결제 완료 로그를 수집하고 정제하는 프로세스")
                    String description) {}

    @Schema(name = "LogProcessUpdateRequest")
    public record UpdateDTO(
            @NotBlank(message = "로그 프로세스 이름은 필수입니다.")
                    @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE)
                    @Schema(description = "로그 프로세스 이름 (파이프라인 프로세스 코드, 영문자로 시작하는 영숫자)", example = "Payment")
                    String name,
            @Size(max = 1000, message = "설명은 1000자를 초과할 수 없습니다.")
                    @Schema(description = "로그 프로세스 설명", example = "결제 완료 로그를 수집하고 정제하는 프로세스")
                    String description) {}
}
