package neobank.infrastructure.util;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaveGeneratorTest {

    private final ClaveGenerator claveGenerator = new ClaveGenerator();

    @Test
    void generatesAnEighteenDigitClabeStartingWithTheBankCode() {
        String clabe = claveGenerator.generateClabe();

        assertThat(clabe).hasSize(18);
        assertThat(clabe).matches("\\d{18}");
        assertThat(clabe).startsWith("646");
    }

    @RepeatedTest(20)
    void checkDigitIsAlwaysConsistentWithTheClabeAlgorithm() {
        String clabe = claveGenerator.generateClabe();
        int[] weights = {3, 7, 1, 3, 7, 1, 3, 7, 1, 3, 7, 1, 3, 7, 1, 3, 7};

        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += Character.getNumericValue(clabe.charAt(i)) * weights[i];
        }
        int expectedCheckDigit = sum % 10 == 0 ? 0 : 10 - (sum % 10);

        assertThat(Character.getNumericValue(clabe.charAt(17))).isEqualTo(expectedCheckDigit);
    }
}
