package neobank;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KycValidatorHandlerTest {

    private final KycValidatorHandler handler = new KycValidatorHandler();

    @Test
    void extractsUserIdFromWellFormedS3Key() {
        assertThat(handler.extractUserIdFromKey("kyc-docs/user-123/selfie.jpg")).isEqualTo("user-123");
    }

    @Test
    void throwsForS3KeyWithoutUserIdSegment() {
        assertThatThrownBy(() -> handler.extractUserIdFromKey("selfie.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid S3 key format");
    }

    @Test
    void reportsLowConfidenceAsTheRejectionReason() {
        assertThat(handler.buildRejectionReason(50.0, true, 90.0, 90.0))
                .contains("confidence too low");
    }

    @Test
    void reportsClosedEyesAsTheRejectionReasonWhenConfidenceIsFine() {
        assertThat(handler.buildRejectionReason(95.0, false, 90.0, 90.0))
                .contains("Eyes must be open");
    }

    @Test
    void reportsBlurAsTheRejectionReasonWhenEyesAndConfidenceAreFine() {
        assertThat(handler.buildRejectionReason(95.0, true, 50.0, 90.0))
                .contains("too blurry");
    }

    @Test
    void reportsDarknessAsTheRejectionReasonWhenEverythingElsePasses() {
        assertThat(handler.buildRejectionReason(95.0, true, 90.0, 50.0))
                .contains("too dark");
    }
}
