package neobank.infrastructure.util;


import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ClaveGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateClabe() {
        StringBuilder clabe = new StringBuilder();

        // Bank code (3 digits) - 646 for example
        clabe.append("646");

        // Branch code (3 digits)
        for (int i = 0; i < 3; i++) {
            clabe.append(RANDOM.nextInt(10));
        }

        // Account number (11 digits)
        for (int i = 0; i < 11; i++) {
            clabe.append(RANDOM.nextInt(10));
        }

        // Check digit (1 digit)
        int checkDigit = calculateCheckDigit(clabe.toString());
        clabe.append(checkDigit);

        return clabe.toString();
    }

    private int calculateCheckDigit(String partialClabe) {
        int[] weights = {3, 7, 1, 3, 7, 1, 3, 7, 1, 3, 7, 1, 3, 7, 1, 3, 7};
        int sum = 0;

        for (int i = 0; i < partialClabe.length(); i++) {
            sum += Character.getNumericValue(partialClabe.charAt(i)) * weights[i];
        }

        int remainder = sum % 10;
        return remainder == 0 ? 0 : 10 - remainder;
    }
}