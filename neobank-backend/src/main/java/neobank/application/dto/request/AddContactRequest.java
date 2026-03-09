package neobank.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddContactRequest {

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^\\d{18}$", message = "Account number must be 18 digits")
    private String accountNumber;

    @Size(max = 100, message = "Nickname must not exceed 100 characters")
    private String nickname;
}