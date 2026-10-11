package com.jurisflow.modules.mcp.server;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.jurisflow.shared.exception.BusinessException;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class McpArguments {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private McpArguments() {}
    // Fields: name:type[:required]. UUIDs, dates and times also advertise their formats.
    public static JsonNode schema(String... fields) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object"); schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        for (String field : fields) {
            String[] parts = field.split(":");
            ObjectNode property = properties.putObject(parts[0]);
            String type = parts[1];
            property.put("type", Set.of("uuid", "date", "time", "priority").contains(type) ? "string" : (type.equals("money") ? "number" : type));
            if (type.equals("uuid") || type.equals("date")) property.put("format", type);
            if (type.equals("time")) property.put("pattern", "^([01][0-9]|2[0-3]):[0-5][0-9]$");
            if (type.equals("priority")) property.putArray("enum").add("BAIXA").add("MEDIA").add("ALTA").add("URGENTE");
            if (type.equals("money")) {
                property.put("minimum", 0.01);
                property.put("isMoney", true);
            }
            if (parts.length > 2) required.add(parts[0]);
            switch (parts[0]) {
                case "pagina" -> { property.put("minimum", 0); property.put("default", 0); }
                case "tamanho" -> { property.put("minimum", 1); property.put("maximum", 50); property.put("default", 20); }
                case "limite" -> { property.put("minimum", 1); property.put("maximum", 100); property.put("default", 20); }
                case "mes" -> { property.put("minimum", 1); property.put("maximum", 12); }
                case "ano" -> { property.put("minimum", 1); property.put("maximum", 9999); }
                case "concluida" -> property.put("default", true);
                case "titulo" -> { property.put("minLength", 2); property.put("maxLength", 500); }
                case "clienteNome" -> { property.put("minLength", 2); property.put("maxLength", 255); }
                default -> { }
            }
        }
        return schema;
    }

    public static void validate(JsonNode args, JsonNode schema) {
        if (args == null || !args.isObject()) invalid();
        for (JsonNode key : schema.path("required")) if (!args.hasNonNull(key.asText())) invalid();
        args.fields().forEachRemaining(entry -> {
            JsonNode property = schema.path("properties").get(entry.getKey());
            if (property == null) invalid();
            JsonNode value = entry.getValue();
            String type = property.path("type").asText();
            if (!(switch (type) {
                case "string" -> value.isTextual();
                case "integer" -> value.isIntegralNumber() && value.canConvertToInt();
                case "number" -> value.isNumber();
                case "boolean" -> value.isBoolean();
                default -> false;
            })) invalid();
            if (value.isNumber()) {
                if (property.has("minimum") && value.doubleValue() < property.get("minimum").asDouble()) invalid();
                if (property.has("maximum") && value.doubleValue() > property.get("maximum").asDouble()) invalid();
                if (property.path("isMoney").asBoolean(false) || "valor".equals(entry.getKey()) || "valorTotal".equals(entry.getKey())) {
                    java.math.BigDecimal bd = value.decimalValue();
                    if (bd.compareTo(java.math.BigDecimal.ZERO) <= 0 || bd.stripTrailingZeros().scale() > 2) invalid();
                }
            }
            if (value.isTextual()) {
                String text = value.asText();
                if (property.has("minLength") && text.trim().length() < property.get("minLength").asInt()) invalid();
                if (property.has("maxLength") && text.length() > property.get("maxLength").asInt()) invalid();
                if (property.has("pattern") && !text.matches(property.get("pattern").asText())) invalid();
                if (property.has("enum")) {
                    boolean found = false;
                    for (JsonNode option : property.get("enum")) if (option.equals(value)) found = true;
                    if (!found) invalid();
                }
                try {
                    if ("uuid".equals(property.path("format").asText())) {
                        if (!UUID.fromString(text).toString().equalsIgnoreCase(text)) invalid();
                    }
                    if ("date".equals(property.path("format").asText())) LocalDate.parse(text);
                } catch (IllegalArgumentException | DateTimeParseException e) { invalid(); }
            }
        });
    }
    private static void invalid() { throw new BusinessException("Argumentos inválidos. Confira o schema da tool."); }
    public static String text(JsonNode args, String name) { return args.hasNonNull(name) ? args.get(name).asText() : null; }
    public static UUID uuid(JsonNode args, String name) { String value = text(args, name); return value == null ? null : UUID.fromString(value); }
    public static int integer(JsonNode args, String name, int fallback) { return args.has(name) ? args.get(name).intValue() : fallback; }
    public static java.math.BigDecimal bigDecimal(JsonNode args, String name) { return args.hasNonNull(name) ? args.get(name).decimalValue() : null; }
}
