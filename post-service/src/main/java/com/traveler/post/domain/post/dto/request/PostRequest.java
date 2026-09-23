package com.traveler.post.domain.post.dto.request;

import com.traveler.post.domain.post.enums.Category;
import com.traveler.post.domain.post.enums.Region;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class PostRequest {

    private PostRequest() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @Schema(name = "PostCreateRequest")
    public record CreateDTO(
            @NotBlank(message = "제목은 필수입니다.")
                    @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다.")
                    @Schema(description = "게시글 제목", example = "경주 황리단길 맛집 투어")
                    String title,
            @NotBlank(message = "내용은 필수입니다.")
                    @Size(max = 2000, message = "내용은 2000자를 초과할 수 없습니다.")
                    @Schema(description = "게시글 내용", example = "어제 다녀온 경주 여행 후기입니다...")
                    String content,
            @NotBlank(message = "여행 장소는 필수입니다.") @Schema(description = "여행 장소 명칭", example = "황리단길") String travelPlace,
            @NotBlank(message = "주소는 필수입니다.") @Schema(description = "상세 주소", example = "경상북도 경주시 포석로") String address,
            @NotNull(message = "카테고리는 필수입니다.") @Schema(description = "카테고리", example = "FOOD") Category category,
            @NotNull(message = "지역은 필수입니다.") @Schema(description = "지역", example = "GYEONGGI") Region region,
            @Size(max = 10, message = "이미지는 최대 10개까지 업로드 가능합니다.")
                    @Schema(
                            description = "S3 이미지 키 리스트",
                            example = "[\"posts/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png\"]")
                    List<String> images) {}

    @Schema(name = "PostUpdateRequest")
    public record UpdateDTO(
            @NotBlank(message = "제목은 필수입니다.")
                    @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다.")
                    @Schema(description = "게시글 제목", example = "경주 황리단길 맛집 투어")
                    String title,
            @NotBlank(message = "내용은 필수입니다.")
                    @Size(max = 2000, message = "내용은 2000자를 초과할 수 없습니다.")
                    @Schema(description = "게시글 내용", example = "어제 다녀온 경주 여행 후기입니다...")
                    String content,
            @NotBlank(message = "여행 장소는 필수입니다.") @Schema(description = "여행 장소 명칭", example = "황리단길") String travelPlace,
            @NotBlank(message = "주소는 필수입니다.") @Schema(description = "상세 주소", example = "경상북도 경주시 포석로") String address,
            @NotNull(message = "카테고리는 필수입니다.") @Schema(description = "카테고리", example = "FOOD") Category category,
            @NotNull(message = "지역은 필수입니다.") @Schema(description = "지역", example = "GYEONGGI") Region region,
            @Size(max = 10, message = "이미지는 최대 10개까지 업로드 가능합니다.")
                    @Schema(
                            description = "S3 이미지 키 리스트",
                            example = "[\"posts/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png\"]")
                    List<String> images) {}
}
