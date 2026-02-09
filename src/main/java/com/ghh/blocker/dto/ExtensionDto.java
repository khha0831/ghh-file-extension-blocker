package com.ghh.blocker.dto;

import com.ghh.blocker.domain.BlockedExtension;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ExtensionDto {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ExtensionDto() {}

    // ===== Request =====

    public record FixedUpdateRequest(

            @NotBlank(message = "확장자를 입력해주세요.")
            @Size(max = 20, message = "확장자는 최대 20자까지 가능합니다.")
            @Pattern(regexp = "^[a-z0-9]+$", message = "영문 소문자와 숫자만 허용됩니다.")
            String extension,

            boolean blocked
    ) {}

    public record CustomAddRequest(

            @NotBlank(message = "확장자를 입력해주세요.")
            @Size(max = 500, message = "입력 길이가 너무 깁니다.")
            @Pattern(regexp = "^[a-z0-9,\\s]+$", message = "영문 소문자, 숫자, 쉼표만 허용됩니다.")
            String extensions
    ) {}

    // ===== Response =====

    public record FixedResponse(
            String extension,
            boolean blocked
    ) {
        public static FixedResponse from(BlockedExtension entity) {
            return new FixedResponse(entity.getExtension(), entity.isBlocked());
        }
    }

    public record CustomResponse(
            Long id,
            String extension,
            String createdAt
    ) {
        public static CustomResponse from(BlockedExtension entity) {
            return new CustomResponse(
                    entity.getId(),
                    entity.getExtension(),
                    formatDateTime(entity.getCreatedAt())
            );
        }
    }

    private static String formatDateTime(LocalDateTime dt) {
        return dt != null ? dt.format(FORMATTER) : "";
    }
}
