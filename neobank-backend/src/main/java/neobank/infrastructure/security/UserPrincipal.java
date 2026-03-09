package neobank.infrastructure.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import neobank.domain.entity.User;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal {

    private UUID id;
    private String cognitoUserId;
    private String email;
    private String fullName;

    public static UserPrincipal create(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getCognitoUserId(),
                user.getEmail(),
                user.getFullName()
        );
    }
}