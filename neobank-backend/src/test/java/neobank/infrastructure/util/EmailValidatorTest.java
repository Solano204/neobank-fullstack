package neobank.infrastructure.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailValidatorTest {

    private final EmailValidator emailValidator = new EmailValidator();

    @ParameterizedTest
    @ValueSource(strings = {"user@neobank.mx", "first.last+tag@sub.domain.com"})
    void acceptsValidEmails(String email) {
        assertThat(emailValidator.isValid(email)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-an-email", "missing@domain", "@nouser.com", "user@.com"})
    void rejectsInvalidEmails(String email) {
        assertThat(emailValidator.isValid(email)).isFalse();
    }
}
