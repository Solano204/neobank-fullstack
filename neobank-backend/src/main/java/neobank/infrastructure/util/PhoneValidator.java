package neobank.infrastructure.util;


import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PhoneValidator {

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[1-9]\\d{1,14}$"
    );

    public boolean isValid(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone).matches();
    }
}