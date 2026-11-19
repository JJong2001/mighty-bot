package mighty.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import mighty.ApplicationInfo;
import mighty.data.Json;

/**
 * 로컬 HTTP 요청을 읽어 정책 또는 기록 처리로 전달합니다.
 */
final class BridgeHttpServer {

  private BridgeHttpServer() {
  }

  static void start(int port) throws IOException {
    String localData = System.getenv("LOCALAPPDATA");
    var records = new BridgeRecords(Path.of(
        localData == null ? System.getProperty("user.home") : localData, "MightyBot", "records"));
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    server.setExecutor(Executors.newFixedThreadPool(2));
    server.createContext("/", exchange -> handle(exchange, records));
    server.start();
    System.out.println("Mighty bot bridge ready: http://127.0.0.1:" + port);
  }

  private static void handle(HttpExchange exchange, BridgeRecords records) throws IOException {
    try (exchange) {
      String path = exchange.getRequestURI().getPath();
      String method = exchange.getRequestMethod();
      Map<String, Object> response;
      int status = 200;
      try {
        if (path.equals("/health") && method.equals("GET")) {
          response = Map.of("ok", true, "service", "mighty-bot-bridge", "version", 1,
              "engineVersion", ApplicationInfo.VERSION, "policy", "rule");
        } else if (method.equals("POST") && (path.equals("/act") || path.equals("/record"))) {
          response = post(exchange, records, path);
        } else {
          status = 404;
          response = Map.of("error", "Unknown route");
        }
      } catch (Exception error) {
        status = 400;
        response = Map.of("error",
            Objects.toString(error.getMessage(), error.getClass().getSimpleName()));
      }
      byte[] body = Json.write(response).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
      exchange.sendResponseHeaders(status, body.length);
      exchange.getResponseBody().write(body);
    }
  }

  private static Map<String, Object> post(HttpExchange exchange, BridgeRecords records, String path)
      throws IOException {
    var headers = exchange.getRequestHeaders();
    if (!"1".equals(headers.getFirst("X-Mighty-Bridge"))
        || !Objects.toString(headers.getFirst("Content-Type"), "").startsWith("application/json")) {
      throw new IllegalArgumentException("Required bridge headers missing");
    }
    byte[] body = exchange.getRequestBody().readNBytes(262145);
    if (body.length > 262144) {
      throw new IllegalArgumentException("Request too large");
    }
    var request = Json.object(Json.read(new String(body, StandardCharsets.UTF_8)));
    if (path.equals("/record")) {
      records.finish(request);
      return Map.of("ok", true);
    }
    var response = BotBridge.choose(request);
    try {
      records.observe(request, response);
    } catch (RuntimeException error) {
      System.err.println("Mighty replay observation: " + error.getMessage());
    }
    return response;
  }

}
