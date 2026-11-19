package mighty.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * 배포 주소에서 엔진만 갱신하고, 새 엔진이 시작되지 않으면 기존 엔진을 실행합니다.
 */
public final class BotLauncher {

  private static final long MAX_JAR_BYTES = 16 * 1024 * 1024;
  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(3))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  public static void main(String[] args) throws Exception {
    Path app = Path.of(
            BotLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI())
        .getParent();
    int port = args.length == 0 ? 18743 : Integer.parseInt(args[0]);
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("포트가 올바르지 않습니다.");
    }
    if (healthy(port, null)) {
      System.out.println("마이티 봇이 이미 실행 중입니다.");
      return;
    }
    Properties config = read(app.resolve("updates.properties"));
    Version bundled = Version.parse(config.getProperty("bundledVersion"));
    Path updates = app.resolve("updates");
    Engine installed = new Engine(bundled, app.resolve("bot-bridge.jar"));
    Path pointer = updates.resolve("active-version");
    try {
      if (Files.isRegularFile(pointer)) {
        Version saved = Version.parse(Files.readString(pointer).strip());
        Path jar = updates.resolve("bot-bridge-" + saved + ".jar");
        if (saved.compareTo(bundled) > 0 && Files.isRegularFile(jar)) {
          installed = new Engine(saved, jar);
        }
      }
    } catch (IOException | IllegalArgumentException ex) {
      System.out.println("저장된 버전을 읽을 수 없어 기본 엔진을 사용합니다.");
    }
    Process bot = null;
    try {
      String url = config.getProperty("manifestUrl", "").strip();
      if (!url.isEmpty()) {
        bot = update(URI.create(url), installed.version(), updates, pointer, port);
      }
    } catch (IOException | IllegalArgumentException ex) {
      System.out.println("업데이트를 확인할 수 없어 기존 엔진을 실행합니다.");
    }
    if (bot == null) {
      bot = start(installed, port);
    }
    if (bot == null && !installed.version().equals(bundled)) {
      bot = start(new Engine(bundled, app.resolve("bot-bridge.jar")), port);
    }
    if (bot == null) {
      throw new IOException("봇을 시작할 수 없습니다. 연결 포트를 확인하세요.");
    }
    Process running = bot;
    Runtime.getRuntime().addShutdownHook(new Thread(running::destroy, "mighty-bot-stop"));
    System.exit(running.waitFor());
  }

  private static Process update(URI manifest, Version installed, Path updates, Path pointer,
      int port)
      throws Exception {
    Properties release = new Properties();
    try (var data = new java.io.ByteArrayInputStream(fetch(manifest, 8192))) {
      release.load(data);
    }
    Version next = Version.parse(release.getProperty("version"));
    if (next.compareTo(installed) <= 0) {
      return null;
    }
    URI asset = manifest.resolve(release.getProperty("jar", ""));
    String expected = release.getProperty("sha256", "");
    if (!expected.matches("[a-fA-F0-9]{64}")) {
      throw new IOException("업데이트 검증 정보 오류");
    }
    byte[] bytes = fetch(asset, MAX_JAR_BYTES);
    String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    if (!actual.equalsIgnoreCase(expected)) {
      throw new IOException("다운로드 검증 실패");
    }
    Files.createDirectories(updates);
    Path candidate = updates.resolve("bot-bridge-" + next + ".jar");
    replace(candidate, bytes);
    Process process = start(new Engine(next, candidate), port);
    if (process == null) {
      System.out.println("새 엔진 시작에 실패하여 기존 버전으로 돌아갑니다.");
      return null;
    }
    try {
      replace(pointer, next.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    } catch (IOException ex) {
      process.destroyForcibly().waitFor();
      throw ex;
    }
    System.out.println("마이티 봇 " + next + " 업데이트 완료");
    return process;
  }

  private static byte[] fetch(URI uri, long limit) throws IOException, InterruptedException {
    // 주소를 검증한 뒤 연결하고, 리디렉션도 같은 규칙으로 검사합니다.
    for (int redirect = 0; redirect < 5; redirect++) {
      requireUpdateUri(uri);
      var connection = (java.net.HttpURLConnection) uri.toURL().openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(3000);
      connection.setReadTimeout(4000);
      try {
        int status = connection.getResponseCode();
        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
          String location = connection.getHeaderField("Location");
          if (location == null) {
            throw new IOException("업데이트 이동 주소 누락");
          }
          uri = uri.resolve(location);
          continue;
        }
        if (status != 200) {
          throw new IOException("업데이트 HTTP " + status);
        }
        try (var body = connection.getInputStream()) {
          byte[] bytes = body.readNBytes((int) limit + 1);
          if (bytes.length > limit) {
            throw new IOException("업데이트 파일 크기 초과");
          }
          return bytes;
        }
      } finally {
        connection.disconnect();
      }
    }
    throw new IOException("업데이트 이동 횟수 초과");
  }

  private static void requireUpdateUri(URI uri) {
    // 루프백은 배포 전 검증에 사용할 수 있습니다. 외부 주소는 HTTPS가 필요합니다.
    if (uri.getHost() == null || uri.getUserInfo() != null
        || (!"https".equalsIgnoreCase(uri.getScheme())
        && !("http".equalsIgnoreCase(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())))) {
      throw new IllegalArgumentException("업데이트 주소는 HTTPS여야 합니다.");
    }
  }

  private static Process start(Engine engine, int port) throws IOException, InterruptedException {
    Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
    Process child = new ProcessBuilder(java.toString(), "-Dfile.encoding=UTF-8",
        "--add-modules", "jdk.httpserver", "-jar", engine.jar().toString(), Integer.toString(port))
        .inheritIO().start();
    for (int check = 0; check < 24 && child.isAlive(); check++) {
      if (healthy(port, engine.version())) {
        return child;
      }
      Thread.sleep(150);
    }
    if (child.isAlive()) {
      child.destroy();
      if (!child.waitFor(2, TimeUnit.SECONDS)) {
        child.destroyForcibly().waitFor();
      }
    }
    return null;
  }

  private static boolean healthy(int port, Version expected) throws InterruptedException {
    var uri = URI.create("http://127.0.0.1:" + port + "/health");
    try {
      var request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(500)).GET().build();
      var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200 && response.body()
          .contains("\"service\":\"mighty-bot-bridge\"")
          && (expected == null || response.body()
          .contains("\"engineVersion\":\"" + expected + "\""));
    } catch (IOException ex) {
      return false;
    }
  }

  private static Properties read(Path path) throws IOException {
    Properties properties = new Properties();
    try (var input = Files.newInputStream(path)) {
      properties.load(input);
    }
    return properties;
  }

  private static void replace(Path target, byte[] bytes) throws IOException {
    Path temp = Files.createTempFile(target.getParent(), ".download-", ".tmp");
    try {
      Files.write(temp, bytes);
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private record Engine(Version version, Path jar) {

  }

  private record Version(int major, int minor, int patch) implements Comparable<Version> {

    static Version parse(String text) {
      if (text == null || !text.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")) {
        throw new IllegalArgumentException("버전 형식 오류");
      }
      String[] pieces = text.split("\\.");
      return new Version(Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1]),
          Integer.parseInt(pieces[2]));
    }

    public int compareTo(Version other) {
      int result = Integer.compare(major, other.major);
      if (result == 0) {
        result = Integer.compare(minor, other.minor);
      }
      return result == 0 ? Integer.compare(patch, other.patch) : result;
    }

    public String toString() {
      return major + "." + minor + "." + patch;
    }
  }

}
