package mighty.game.rule;

import static mighty.model.Rank.*;

import java.util.*;
import mighty.game.GameMode;
import mighty.game.TrickNumber;
import mighty.game.action.Action;
import mighty.game.action.JokerLead;
import mighty.game.action.StandardPlay;
import mighty.game.trick.PublicPlay;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;

/**
 * 사용자가 확인한 테이블 룰: 합법 행동, 승패, 비딩 변경과 명목 정산을 계산한다.
 */
public final class Rules {

  private static final int MIGHTY_STRENGTH = 400;
  private static final int JOKER_STRENGTH = 300;
  private static final int TRUMP_BASE = 200;
  private static final int LEAD_SUIT_BASE = 100;
  private static final int INACTIVE_JOKER_STRENGTH = -1;

  private Rules() {
  }

  public static StandardCard mighty(Trump trump) {
    Objects.requireNonNull(trump, "기루 설정이 필요합니다.");
    Suit suit = trump == Trump.SPADE
        ? Suit.DIAMOND
        : Suit.SPADE;
    return new StandardCard(suit, ACE);
  }

  public static StandardCard gun(Trump trump) {
    Objects.requireNonNull(trump, "기루 설정이 필요합니다.");
    Suit suit = trump == Trump.CLUB
        ? Suit.SPADE
        : Suit.CLUB;
    return new StandardCard(suit, THREE);
  }

  public static int strength(Card card, TrickConditions conditions) {
    Objects.requireNonNull(card, "비교할 카드가 필요합니다.");
    Objects.requireNonNull(conditions, "구 조건이 필요합니다.");
    if (card.equals(mighty(conditions.trump()))) {
      return MIGHTY_STRENGTH;
    }
    if (card instanceof Joker) {
      boolean inactive = conditions.trickNumber().isFirst()
          || conditions.trickNumber().isLast()
          || conditions.jokerCalled();
      return inactive ? INACTIVE_JOKER_STRENGTH : JOKER_STRENGTH;
    }
    if (card instanceof StandardCard ordinary) {
      int rank = ordinary.rank().value();
      if (conditions.trump().matches(ordinary.suit())) {
        return TRUMP_BASE + rank;
      }
      if (ordinary.suit() == conditions.leadSuit()) {
        return LEAD_SUIT_BASE + rank;
      }
      return rank;
    }
    throw new IllegalArgumentException(
        "지원하지 않는 카드 타입: " + card.getClass().getName()
    );
  }

  public static TrickConditions conditionsFor(
      Trump trump,
      TrickNumber trickNumber,
      Action opening
  ) {
    Objects.requireNonNull(trump, "기루 설정이 필요합니다.");
    Objects.requireNonNull(opening, "선의 행동이 필요합니다.");
    if (opening instanceof StandardPlay play) {
      if (play.call() && !play.card().equals(gun(trump))) {
        throw new IllegalArgumentException(
            "총으로만 조커콜을 선언할 수 있습니다."
        );
      }
      return new TrickConditions(
          trump,
          play.card().suit(),
          trickNumber,
          play.call()
      );
    }
    if (opening instanceof JokerLead lead) {
      return new TrickConditions(
          trump,
          lead.declaredSuit(),
          trickNumber,
          false
      );
    }
    throw new IllegalArgumentException(
        "조커 따라내기를 선의 행동으로 사용할 수 없습니다."
    );
  }

  public static Optional<PublicPlay> currentWinner(
      Trump trump,
      TrickNumber trickNumber,
      List<PublicPlay> trick
  ) {
    Objects.requireNonNull(trump, "기루 설정이 필요합니다.");
    List<PublicPlay> plays = List.copyOf(trick);
    if (plays.size() > GameMode.PLAYING_PLAYER_COUNT) {
      throw new IllegalArgumentException(
          "한 구의 제출은 최대 "
              + GameMode.PLAYING_PLAYER_COUNT
              + "개입니다."
      );
    }
    if (plays.isEmpty()) {
      return Optional.empty();
    }
    TrickConditions conditions = conditionsFor(trump, trickNumber, plays.get(0).action());
    return plays.stream()
        .max(Comparator.comparingInt(
            play -> strength(play.action().card(), conditions)
        ));
  }

}
