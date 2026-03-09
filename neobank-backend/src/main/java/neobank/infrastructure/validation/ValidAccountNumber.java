package neobank.infrastructure.validation;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AccountNumberValidator.class)
@Documented
public @interface ValidAccountNumber {
    String message() default "Invalid account number format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}