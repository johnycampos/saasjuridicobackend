package com.jurisflow.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que uma string, quando presente, e uma URL absoluta com esquema
 * http/https. Usado em campos de link fornecidos pelo usuario (ex:
 * ProcessoLinkRequest.url) para bloquear esquemas como javascript:/data: —
 * ver docs/security-audit/relatorio-auditoria-seguranca.md (Stored XSS).
 * Uma string em branco/nula e considerada valida aqui; combine com
 * @NotBlank quando o campo for obrigatorio.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeUrlValidator.class)
public @interface SafeUrl {
    String message() default "URL invalida ou esquema nao permitido (use http:// ou https://)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
