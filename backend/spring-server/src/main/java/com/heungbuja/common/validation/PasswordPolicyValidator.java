package com.heungbuja.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 비밀번호 정책 검증 구현체
 */
public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            return false;
        }

        // 최소 8자 이상
        if (password.length() < 8) {
            return false;
        }

        // 영문 대문자 포함
        if (!password.matches(".*[A-Z].*")) {
            return false;
        }

        // 영문 소문자 포함
        if (!password.matches(".*[a-z].*")) {
            return false;
        }

        // 숫자 포함
        if (!password.matches(".*\\d.*")) {
            return false;
        }

        // 특수문자 포함
        if (!password.matches(".*[@$!%*#?&].*")) {
            return false;
        }

        return true;
    }
}
