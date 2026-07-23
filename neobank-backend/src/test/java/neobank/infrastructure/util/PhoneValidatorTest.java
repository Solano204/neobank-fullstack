package neobank.infrastructure.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneValidatorTest {

    private final PhoneValidator phoneValidator = new PhoneValidator();

    @ParameterizedTest
    @ValueSource(strings = {"+525512345678", "5512345678"})
    void acceptsValidPhoneNumbers(String phone) {
        assertThat(phoneValidator.isValid(phone)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc123", "+0123456789", "1"})
    void rejectsInvalidPhoneNumbers(String phone) {
        assertThat(phoneValidator.isValid(phone)).isFalse();
    }
}
