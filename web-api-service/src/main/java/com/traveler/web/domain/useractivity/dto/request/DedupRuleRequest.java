package com.traveler.web.domain.useractivity.dto.request;

import com.traveler.web.domain.useractivity.dto.DedupSpec;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class DedupRuleRequest {
    private DedupRuleRequest() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @Schema(name = "DedupRuleCreateRequest")
    public record CreateDTO(
            @NotBlank(message = "중복제거 규칙 이름은 필수입니다.") @Schema(description = "중복제거 규칙 이름", example = "IP 기준 중복제거")
                    String name,
            @NotEmpty(message = "최소 하나 이상의 규칙이 필요합니다.") @Valid @Schema(description = "중복제거 규칙 목록")
                    List<DedupSpec> rules,
            @NotNull(message = "활성화 여부는 필수입니다.") @Schema(description = "활성화 여부", example = "true") Boolean isActive) {}

    @Schema(name = "DedupRuleUpdateRequest")
    public record UpdateDTO(
            @NotBlank(message = "중복제거 규칙 이름은 필수입니다.") @Schema(description = "중복제거 규칙 이름", example = "IP 기준 중복제거")
                    String name,
            @NotEmpty(message = "최소 하나 이상의 규칙이 필요합니다.") @Valid @Schema(description = "중복제거 규칙 목록")
                    List<DedupSpec> rules,
            @NotNull(message = "활성화 여부는 필수입니다.") @Schema(description = "활성화 여부", example = "true") Boolean isActive) {}
}
