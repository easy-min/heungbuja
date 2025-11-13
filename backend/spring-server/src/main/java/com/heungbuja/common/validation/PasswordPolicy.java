package com.heungbuja.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호 정책 검증 어노테이션
 * - 최소 8자 이상
 * - 영문 대문자 포함
 * - 영문 소문자 포함
 * - 숫자 포함
 * - 특수문자 포함
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordPolicyValidator.class)
public @interface PasswordPolicy {

    String message() default "비밀번호는 최소 8자 이상, 영문 대소문자/숫자/특수문자를 포함해야 합니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
