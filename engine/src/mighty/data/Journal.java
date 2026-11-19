package mighty.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import mighty.ApplicationInfo;
import mighty.game.Seat;

/**
 * 과거 기록은 읽기 호환으로 보존하고 새 판단·피드백은 현재 형식으로 이어 씁니다.
 */
public final class Journal {

  private final Path directory;
  private int readWarnings;

  public Journal(Path directory) throws IOException {
    this.directory = directory.toAbsolutePath();
    Files.createDirectories(this.directory);
  }

  public Path directory() {
    return directory;
  }

  public int readWarnings() {
    return readWarnings;
  }

  /**
   * 화면에 공개해도 되는 원래 판단입니다. 구형 엔진 코드에 의존하지 않습니다.
   */
  public record Review(String id, String version, String gameId, int seat, String stage,
                       String selected, List<String> alternatives, String reason,
                       Map<String, Object> snapshot,
                       String feedback, boolean publicOnly) {

    public Review {
      alternatives = List.copyOf(alternatives);
      snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
    }
  }

  /**
   * 현재 엔진의 자기 관측과 실제 선택을 원래 버전·출처와 함께 기록합니다.
   */
  public String decision(String gameId, Seat seat, String stage, Map<String, Object> snapshot,
      String selected, List<String> alternatives, String reason, String source) throws IOException {
    String id = UUID.randomUUID().toString();
    Map<String, Object> record = header("decision");
    record.put("id", id);
    record.put("schemaVersion", 2);
    record.put("gameId", gameId);
    record.put("seat", seat.index());
    record.put("stage", stage);
    record.put("snapshot", snapshot);
    record.put("selected", selected);
    record.put("alternatives", alternatives);
    record.put("reason", reason);
    record.put("source", source);
    append(record);
    return id;
  }

  /**
   * 종료·중단 정보는 복기 창의 상대 비공개 정보 공개 시점을 결정합니다.
   */
  public void gameEvent(String gameId, String event, String detail) throws IOException {
    Map<String, Object> record = header("game-" + event);
    record.put("gameId", gameId);
    record.put("detail", detail);
    append(record);
  }

  /**
   * 새 형식과 과거 JSON 원본을 함께 읽고, 진행 중인 상대의 비공개 필드를 가립니다.
   */
  public List<Review> reviews() throws IOException {
    readWarnings = 0;
    Path file = directory.resolve("training.jsonl");
    if (!Files.exists(file)) {
      return List.of();
    }
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    List<Map<String, Object>> records = new ArrayList<>();
    for (String line : lines.subList(Math.max(0, lines.size() - 5000), lines.size())) {
      if (line.isBlank()) {
        continue;
      }
      try {
        records.add(Json.object(Json.read(line)));
      } catch (RuntimeException invalid) {
        readWarnings++;
      }
    }
    Set<String> closed = new HashSet<>();
    Map<String, String> feedback = new HashMap<>();
    Map<String, String> games = new HashMap<>();
    Map<String, Integer> seats = new HashMap<>();
    Pattern oldPlay = Pattern.compile("seat=(\\d+); decisionId=([^;]+)");
    for (Map<String, Object> record : records) {
      String type = string(record, "type", "");
      if (type.equals("game-result") || type.equals("game-abandoned")) {
        closed.add(string(record, "gameId", ""));
      }
      if (type.equals("feedback")) {
        feedback.put(string(record, "decisionId", ""), string(record, "verdict", "")
            + " · " + string(record, "note", ""));
      }
      if (type.equals("game-play")) {
        var matcher = oldPlay.matcher(string(record, "detail", ""));
        if (matcher.find()) {
          seats.put(matcher.group(2), Integer.parseInt(matcher.group(1)));
          games.put(matcher.group(2), string(record, "gameId", ""));
        }
      }
    }
    List<Review> reviews = new ArrayList<>();
    for (Map<String, Object> record : records) {
      String type = string(record, "type", "");
      if (!type.equals("decision") && !type.equals("setup-decision")) {
        continue;
      }
      String id = string(record, "id", "");
      if (id.isBlank()) {
        continue;
      }
      String game = string(record, "gameId", games.getOrDefault(id, ""));
      int seat = Json.integer(record, "seat", seats.getOrDefault(id, -1));
      Map<String, Object> snapshot = objectOrEmpty(record.get("snapshot"));
      if (snapshot.isEmpty()) {
        snapshot = objectOrEmpty(record.get("view"));
      }
      String stage = string(record, "stage", "PLAY");
      if (stage.equals("PLAY")) {
        stage += " / " + Json.integer(snapshot, "trickNumber", 1) + "구";
      }
      boolean hidden = !game.isBlank() && !closed.contains(game) && seat != 0;
      if (hidden && !stage.equals("BIDDING")) {
        continue;
      }
      List<String> alternatives = new ArrayList<>();
      Object savedAlternatives = record.get("alternatives");
      if (savedAlternatives instanceof List<?> list) {
        for (Object value : list) {
          alternatives.add(value.toString());
        }
      }
      String selected = string(record, "selected", "");
      String reason = string(record, "reason", "당시 설명 없음");
      if (record.get("ranking") instanceof List<?> ranking) {
        for (Object raw : ranking) {
          Map<String, Object> candidate = objectOrEmpty(raw);
          String action = string(candidate, "action", "");
          if (!action.isBlank()) {
            alternatives.add(action);
          }
          if (action.equals(selected)) {
            reason = string(candidate, "reason", reason);
          }
        }
      }
      reason = "선택 출처: " + string(record, "source", "당시 기록") + "\n" + reason;
      if (hidden) {
        // 손패와 상세 평가를 복사하지 않는 공개 필드 허용 목록입니다.
        Map<String, Object> publicSnapshot = new LinkedHashMap<>();
        for (String key : List.of("currentBid", "publicAuction", "contractHistory")) {
          if (snapshot.containsKey(key)) {
            publicSnapshot.put(key, snapshot.get(key));
          }
        }
        snapshot = publicSnapshot;
        alternatives = List.of();
        reason = "공개된 입찰·패스만 표시합니다. 비공개 정보는 대국 종료·중단 후 공개됩니다.";
      }
      reviews.add(new Review(id, string(record, "engineVersion", "과거 버전"), game, seat,
          stage, selected, alternatives, reason, snapshot, feedback.getOrDefault(id, ""), hidden));
    }
    Collections.reverse(reviews);
    return List.copyOf(reviews);
  }

  /**
   * 피드백과 당시 증거를 보존하며, 사람의 실수를 자동 정답으로 학습하지 않습니다.
   */
  public void feedback(Review review, String verdict, String alternative, String note)
      throws IOException {
    if (note == null || note.isBlank()) {
      throw new IllegalArgumentException("피드백 이유를 입력하세요.");
    }
    if (!Set.of("잘못된 선택", "좋은 선택", "검토 필요").contains(verdict)) {
      throw new IllegalArgumentException("피드백 분류가 올바르지 않습니다.");
    }
    if (!alternative.isBlank() && !review.alternatives().isEmpty()
        && !review.alternatives().contains(alternative)) {
      throw new IllegalArgumentException("당시 선택지에 없는 대안입니다.");
    }
    Map<String, Object> record = header("feedback");
    record.put("id", UUID.randomUUID().toString());
    record.put("decisionId", review.id());
    record.put("gameId", review.gameId());
    record.put("originalEngineVersion", review.version());
    record.put("originalAction", review.selected());
    record.put("originalObservation", review.snapshot());
    record.put("verdict", verdict);
    record.put("alternative", alternative);
    record.put("note", note);
    record.put("applyExact", false);
    append(record);
  }

  private static Map<String, Object> header(String type) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("type", type);
    record.put("timestamp", Instant.now().toString());
    record.put("engineVersion", ApplicationInfo.VERSION);
    return record;
  }

  private synchronized void append(Map<String, Object> record) throws IOException {
    Files.writeString(directory.resolve("training.jsonl"), Json.write(record) + "\n",
        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  private static String string(Map<String, Object> record, String key, String fallback) {
    return record.get(key) instanceof String value ? value : fallback;
  }

  private static Map<String, Object> objectOrEmpty(Object value) {
    return value instanceof Map<?, ?> ? Json.object(value) : Map.of();
  }

}
