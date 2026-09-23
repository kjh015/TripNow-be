package com.traveler.post.global.s3.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** {@link com.traveler.post.global.s3.ImageType}에 등록된 MIME 타입만 허용한다. null은 다른 제약(@NotBlank)에 맡긴다. */
@Documented
@Constraint(validatedBy = AllowedImageContentTypeValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedImageContentType {
    String message() default "허용되지 않은 파일 형식입니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
