package neobank.application.dto.request;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsRequest {

    private NotificationsDto notifications;
    private SecurityDto security;
    private PreferencesDto preferences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationsDto {
        private Boolean email;
        private Boolean push;
        private Boolean sms;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityDto {
        private Boolean mfaEnabled;
        private Boolean biometricEnabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreferencesDto {
        private String language;
        private String currency;
        private String theme;
    }
}