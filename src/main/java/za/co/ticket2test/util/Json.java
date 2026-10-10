package za.co.ticket2test.util;

import java.util.*;

/** Minimal JSON parser/serializer so Ticket2Test stays dependency-light. */
public final class Json {
    private Json() {}

    public static Object parse(String json) {
        return new Parser(json).parse();
    }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> object(Object value) {
        return value instanceof Map<?,?> map ? (Map<String,Object>) map : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : new ArrayList<>();
    }

    public static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(string(value));
    }

    public static double number(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(string(value)); } catch (Exception e) { return 0d; }
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof String s) { out.append('"').append(escape(s)).append('"'); return; }
        if (value instanceof Number || value instanceof Boolean) { out.append(value); return; }
        if (value instanceof Map<?,?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?,?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                out.append('"').append(escape(String.valueOf(entry.getKey()))).append('"').append(':');
                write(out, entry.getValue());
            }
            out.append('}');
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) out.append(',');
                first = false;
                write(out, item);
            }
            out.append(']');
            return;
        }
        out.append('"').append(escape(String.valueOf(value))).append('"');
    }

    public static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static final class Parser {
        private final String s;
        private int i;
        Parser(String s) { this.s = s == null ? "" : s; }

        Object parse() {
            skip();
            Object v = value();
            skip();
            if (i != s.length()) throw error("Unexpected trailing content");
            return v;
        }

        private Object value() {
            skip();
            if (i >= s.length()) throw error("Unexpected end of JSON");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || Character.isDigit(c)) yield number();
                    throw error("Unexpected character '" + c + "'");
                }
            };
        }

        private Map<String,Object> object() {
            expect('{'); skip();
            Map<String,Object> map = new LinkedHashMap<>();
            if (peek('}')) { i++; return map; }
            while (true) {
                skip();
                String key = string();
                skip(); expect(':');
                Object value = value();
                map.put(key, value);
                skip();
                if (peek('}')) { i++; return map; }
                expect(',');
            }
        }

        private List<Object> array() {
            expect('['); skip();
            List<Object> list = new ArrayList<>();
            if (peek(']')) { i++; return list; }
            while (true) {
                list.add(value());
                skip();
                if (peek(']')) { i++; return list; }
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (i >= s.length()) throw error("Bad escape");
                char e = s.charAt(i++);
                switch (e) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw error("Bad unicode escape");
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw error("Bad escape \\" + e);
                }
            }
            throw error("Unterminated string");
        }

        private Object literal(String word, Object value) {
            if (!s.startsWith(word, i)) throw error("Expected " + word);
            i += word.length();
            return value;
        }

        private Number number() {
            int start = i;
            if (peek('-')) i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            boolean decimal = false;
            if (peek('.')) { decimal = true; i++; while (i < s.length() && Character.isDigit(s.charAt(i))) i++; }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                decimal = true; i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            String n = s.substring(start, i);
            try { return decimal ? Double.parseDouble(n) : Long.parseLong(n); }
            catch (NumberFormatException e) { throw error("Invalid number"); }
        }

        private void expect(char c) {
            skip();
            if (i >= s.length() || s.charAt(i) != c) throw error("Expected '" + c + "'");
            i++;
        }
        private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }
        private void skip() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private IllegalArgumentException error(String msg) { return new IllegalArgumentException(msg + " at index " + i); }
    }
}
