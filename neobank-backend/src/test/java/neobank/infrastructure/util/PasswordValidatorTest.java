package neobank.infrastructure.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidatorTest {

    private final PasswordValidator passwordValidator = new PasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"StrongPass1!", "An0ther$Valid1"})
    void acceptsPasswordsMeetingAllRequirements(String password) {
        assertThat(passwordValidator.isValid(password)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short1!", "nouppercase1!", "NOLOWERCASE1!", "NoDigits!!", "NoSpecialChar1"})
    void rejectsPasswordsMissingRequirements(String password) {
        assertThat(passwordValidator.isValid(password)).isFalse();
    }

    @Test
    void exposesHumanReadableRequirements() {
        assertThat(passwordValidator.getRequirements()).contains("8 characters");
    }
}
