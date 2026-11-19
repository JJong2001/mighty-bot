package mighty.data;

import java.util.*;

/**
 * 외부 라이브러리 없이 로컬 기록에 필요한 JSON을 읽고 쓴다.
 */
public final class Json {

  private Json() {
  }

  public static String write(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String s) {
      return quote(s);
    }
    if (value instanceof Boolean || value instanceof Number) {
      return value.toString();
    }
    if (value instanceof Map<?, ?> m) {
      return "{" + String.join(",",
          m.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString()))
              .map(e -> write(e.getKey().toString()) + ":" + write(e.getValue())).toList()) + "}";
    }
    if (value instanceof Collection<?> c) {
      return "[" + String.join(",", c.stream().map(Json::write).toList()) + "]";
    }
    throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
  }

  public static Object read(String text) {
    Parser p = new Parser(text);
    Object value = p.value(0);
    p.space();
    if (p.at != text.length()) {
      throw new IllegalArgumentException("JSON trailing data");
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> object(Object value) {
    if (!(value instanceof Map<?, ?>)) {
      throw new IllegalArgumentException("JSON object required");
    }
    return (Map<String, Object>) value;
  }

  public static int integer(Map<String, Object> m, String k, int fallback) {
    return m.get(k) instanceof Number n ? n.intValue() : fallback;
  }

  private static String quote(String value) {
    StringBuilder b = new StringBuilder("\"");
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        default -> {
          if (c < 32) {
            b.append(String.format("\\u%04x", (int) c));
          } else {
            b.append(c);
          }
        }
      }
    }
    return b.append('"').toString();
  }

  private static final class Parser {

    private final String text;
    private int at;

    Parser(String text) {
      this.text = text;
    }

    void space() {
      while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
        at++;
      }
    }

    char take() {
      if (at >= text.length()) {
        throw new IllegalArgumentException("Incomplete JSON");
      }
      return text.charAt(at++);
    }

    Object value(int depth) {
      if (depth > 64) {
        throw new IllegalArgumentException("JSON nesting too deep");
      }
      space();
      char c = take();
      if (c == '"') {
        return string();
      }
      if (c == '{') {
        Map<String, Object> m = new LinkedHashMap<>();
        space();
        if (at < text.length() && text.charAt(at) == '}') {
          at++;
          return m;
        }
        while (true) {
          space();
          if (take() != '"') {
            throw new IllegalArgumentException("JSON key required");
          }
          String key = string();
          space();
          if (take() != ':') {
            throw new IllegalArgumentException("JSON colon required");
          }
          if (m.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate JSON key");
          }
          m.put(key, value(depth + 1));
          space();
          char end = take();
          if (end == '}') {
            return m;
          }
          if (end != ',') {
            throw new IllegalArgumentException("JSON comma required");
          }
        }
      }
      if (c == '[') {
        List<Object> list = new ArrayList<>();
        space();
        if (at < text.length() && text.charAt(at) == ']') {
          at++;
          return list;
        }
        while (true) {
          list.add(value(depth + 1));
          space();
          char end = take();
          if (end == ']') {
            return list;
          }
          if (end != ',') {
            throw new IllegalArgumentException("JSON comma required");
          }
        }
      }
      at--;
      int start = at;
      while (at < text.length() && ",]} \r\n\t".indexOf(text.charAt(at)) < 0) {
        at++;
      }
      String token = text.substring(start, at);
      return switch (token) {
        case "true" -> true;
        case "false" -> false;
        case "null" -> null;
        default -> {
          if (!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
            throw new IllegalArgumentException("Invalid JSON token");
          }
          yield Double.valueOf(token);
        }
      };
    }

    String string() {
      StringBuilder b = new StringBuilder();
      while (true) {
        char c = take();
        if (c == '"') {
          return b.toString();
        }
        if (c < 32) {
          throw new IllegalArgumentException("Unescaped JSON control");
        }
        if (c != '\\') {
          b.append(c);
          continue;
        }
        char e = take();
        switch (e) {
          case '"', '\\', '/' -> b.append(e);
          case 'n' -> b.append('\n');
          case 'r' -> b.append('\r');
          case 't' -> b.append('\t');
          case 'b' -> b.append('\b');
          case 'f' -> b.append('\f');
          case 'u' -> {
            if (at + 4 > text.length()) {
              throw new IllegalArgumentException("Incomplete unicode escape");
            }
            b.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
            at += 4;
          }
          default -> throw new IllegalArgumentException("Invalid JSON escape");
        }
      }
    }
  }

}
