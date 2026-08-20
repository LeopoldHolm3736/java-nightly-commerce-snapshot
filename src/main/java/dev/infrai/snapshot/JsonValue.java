package dev.infrai.snapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonValue {
    private JsonValue() {}

    static Map<String, Object> object(String text) {
        Object value = new Parser(text).parse();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected JSON object");
        @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class Parser {
        private final String text;
        private int at;
        Parser(String text) { this.text = text; }

        Object parse() {
            Object value = value();
            space();
            if (at != text.length()) throw new IllegalArgumentException("Trailing JSON content");
            return value;
        }

        private Object value() {
            space();
            if (at >= text.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return switch (text.charAt(at)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            at++;
            space();
            if (take('}')) return result;
            do {
                space();
                String key = string();
                space();
                expect(':');
                result.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> array() {
            List<Object> result = new ArrayList<>();
            at++;
            space();
            if (take(']')) return result;
            do { result.add(value()); space(); } while (take(','));
            expect(']');
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                char escaped = text.charAt(at++);
                if (escaped == 'u') {
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                } else {
                    out.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                        default -> throw new IllegalArgumentException("Invalid JSON escape");
                    });
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object number() {
            int start = at;
            while (at < text.length() && "-+0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            String raw = text.substring(start, at);
            return raw.contains(".") || raw.contains("e") || raw.contains("E") ? Double.valueOf(raw) : Long.valueOf(raw);
        }

        private Object literal(String raw, Object value) {
            if (!text.startsWith(raw, at)) throw new IllegalArgumentException("Invalid JSON literal");
            at += raw.length();
            return value;
        }

        private void space() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }
        private boolean take(char c) { if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
        private void expect(char c) { if (!take(c)) throw new IllegalArgumentException("Expected '" + c + "'"); }
    }
}
