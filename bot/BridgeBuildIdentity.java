package mighty.bridge;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/**
 * 버전 번호가 같아도 실행 중인 JAR이 같은 빌드인지 구분합니다.
 */
final class BridgeBuildIdentity {

  private static final String SHA256 = calculate();

  private BridgeBuildIdentity() {
  }

  static String sha256() {
    return SHA256;
  }

  private static String calculate() {
    try {
      var location = Path.of(
          BridgeBuildIdentity.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      if (!Files.isRegularFile(location)) {
        return "unpackaged";
      }
      var digest = MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(location)) {
        var buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
          digest.update(buffer, 0, count);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException | java.net.URISyntaxException error) {
      throw new IllegalStateException("실행 중인 BOT 빌드를 확인할 수 없습니다.", error);
    }
  }
}
