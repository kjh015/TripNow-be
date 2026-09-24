package com.traveler.post.global.s3.validation;

import com.traveler.post.global.s3.ImageType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AllowedImageContentTypeValidator implements ConstraintValidator<AllowedImageContentType, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || ImageType.isAllowedContentType(value);
    }
}
