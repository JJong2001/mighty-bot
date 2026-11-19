package mighty.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mighty.ApplicationInfo;
import mighty.data.Json;

/**
 * 로컬 옵션을 켠 경우에만 기록하며, 복기 자료는 정산이 끝난 판에 한해 저장합니다.
 */
final class BridgeRecords {

  private final Path directory;
  private final Map<String, Map<String, Map<String, Object>>> observations = new LinkedHashMap<>();

  BridgeRecords(Path directory) {
    this.directory = directory;
  }

  synchronized void observe(Map<String, Object> request, Map<String, Object> response) {
    if (!enabled("replay.enabled") || !"play".equals(request.get("phase"))) {
      return;
    }
    String roundId = id(request.get("roundId"));
    if (roundId.isEmpty()) {
      return;
    }
    var round = observations.computeIfAbsent(roundId, ignored -> new LinkedHashMap<>());
    String turn = Json.write(
        List.of(request.get("self"), request.getOrDefault("history", List.of()),
            request.getOrDefault("trick", List.of())));
    if (round.size() < 100 || round.containsKey(turn)) {
      round.put(turn, Map.of("observation", request, "decision", response));
    }
    if (observations.size() > 16) {
      observations.remove(observations.keySet().iterator().next());
    }
  }

  synchronized void finish(Map<String, Object> result) throws IOException {
    String roundId = id(result.get("roundId"));
    if (roundId.isEmpty()) {
      throw new IllegalArgumentException("Invalid round ID");
    }
    if (enabled("replay.enabled")) {
      var round = observations.get(roundId);
      if (round != null && !round.isEmpty()) {
        Path replays = directory.resolve("replays");
        Files.createDirectories(replays);
        Path target = replays.resolve(roundId + ".json");
        if (!Files.exists(target)) {
          String data = Json.write(Map.of("schema", 1, "engineVersion", ApplicationInfo.VERSION,
              "result", result, "decisions", new ArrayList<>(round.values())));
          Path temp = Files.createTempFile(replays, "round-", ".tmp");
          try {
            Files.writeString(temp, data);
            Files.move(temp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
          } finally {
            Files.deleteIfExists(temp);
          }
        }
      }
    }
    if (enabled("scores.enabled")) {
      writeScores(result, roundId);
    }
    observations.remove(roundId);
  }

  private void writeScores(Map<String, Object> result, String roundId) throws IOException {
    Path csv = directory.resolve("scores.csv");
    boolean exists = Files.exists(csv);
    if (exists) {
      try (var lines = Files.lines(csv)) {
        if (lines.anyMatch(line -> line.startsWith(roundId + ","))) {
          return;
        }
      }
    }
    if (!(result.get("scores") instanceof List<?> scores) || scores.size() < 5 || scores.size() > 6
        || !(result.get("contract") instanceof Map<?, ?> contract)) {
      throw new IllegalArgumentException("Invalid score record");
    }
    String trump = String.valueOf(contract.get("trump"));
    if (!trump.matches("[SDHCN]") || !(result.get("success") instanceof Boolean)) {
      throw new IllegalArgumentException("Invalid contract");
    }
    var rows = new StringBuilder();
    if (!exists) {
      rows.append("round_id,contract,trump,success,team_points,seat,before,after,change\n");
    }
    for (Object entry : scores) {
      Map<String, Object> seat = Json.object(entry);
      String color = String.valueOf(seat.get("seat"));
      if (!color.matches("Red|Blue|White|Green|Yellow|Purple|Orange|Pink|Teal|Brown")) {
        throw new IllegalArgumentException("Invalid seat");
      }
      int before = integer(seat.get("before"));
      int after = integer(seat.get("after"));
      rows.append(roundId).append(',').append(integer(contract.get("points"))).append(',')
          .append(trump).append(',').append(result.get("success")).append(',')
          .append(integer(result.get("teamPoints"))).append(',').append(color).append(',')
          .append(before).append(',').append(after).append(',').append(after - before).append('\n');
    }
    Files.writeString(csv, rows, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  private boolean enabled(String flag) {
    return Files.isRegularFile(directory.resolve(flag));
  }

  private static int integer(Object value) {
    if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
      throw new IllegalArgumentException("Invalid score number");
    }
    return number.intValue();
  }

  private static String id(Object value) {
    return value instanceof String text && text.matches("[0-9]+-[0-9]+-[0-9]+") ? text : "";
  }

}
