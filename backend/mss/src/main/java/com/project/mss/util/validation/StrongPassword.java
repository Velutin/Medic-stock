package com.project.mss.util.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Single password policy: 8 to 20 characters with a digit, a lowercase letter, an uppercase letter and a special character. */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@NotBlank
@Size(min = 8, max = 20)
@Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).*$")
public @interface StrongPassword {
    String message() default "Password must have 8 to 20 characters, with at least one digit, one lowercase letter, "
            + "one uppercase letter and one special character";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
