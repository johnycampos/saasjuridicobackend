package com.jurisflow.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class SafeUrlValidator implements ConstraintValidator<SafeUrl, String> {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // obrigatoriedade e responsabilidade de @NotBlank
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            return scheme != null && ALLOWED_SCHEMES.contains(scheme.toLowerCase()) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
