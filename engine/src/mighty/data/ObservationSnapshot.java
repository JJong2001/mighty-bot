package mighty.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mighty.game.observation.DeclarerInfo;
import mighty.game.observation.View;
import mighty.model.Card;

/**
 * 현재 행동자의 허용된 관측만 기록용 값으로 변환합니다. 다른 사람의 패는 받지 않습니다.
 */
public final class ObservationSnapshot {

  private ObservationSnapshot() {
  }

  public static String cards(List<Card> cards) {
    return String.join(" ", cards.stream().map(Card::toCode).toList());
  }

  /**
   * 원래 판단을 보존하기 위한 스냅샷이며 구형 관측을 새 객체로 추측 복원하지 않습니다.
   */
  public static Map<String, Object> play(View view) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("self", view.self().index());
    snapshot.put("hand", cards(view.hand()));
    snapshot.put("role", view.role().name());
    snapshot.put("contract", view.contract().points() + ":" + view.contract().trump().name());
    snapshot.put("trickNumber", view.trickNumber().value());
    snapshot.put("friendSelection", view.friendSelection().toString());
    snapshot.put("declarer", view.context().declarer().index());
    snapshot.put("friendStatus", view.context().friendStatus().toString());
    snapshot.put("contractHistory", view.context().contractHistory().stream()
        .map(bid -> bid.points() + ":" + bid.trump().name()).toList());
    snapshot.put("trick", view.trick().stream().map(play -> Map.of(
        "seat", play.seat().index(), "action", ActionConverter.encode(play.action()))).toList());
    snapshot.put("history", view.context().history().stream().map(trick -> Map.of(
        "number", trick.number().value(), "winner", trick.winner().index(),
        "cards", trick.cards().stream().map(play -> Map.of("seat", play.seat().index(),
            "action", ActionConverter.encode(play.action()))).toList())).toList());
    if (view.privateInfo() instanceof DeclarerInfo declarer) {
      snapshot.put("ownDiscards", cards(declarer.discardedCards()));
    }
    return Map.copyOf(snapshot);
  }

}
