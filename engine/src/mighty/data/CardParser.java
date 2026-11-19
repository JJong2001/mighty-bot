package mighty.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;

public final class CardParser {

  private CardParser() {
  }

  public static Card parse(String value) {
    Objects.requireNonNull(value, "카드 입력이 필요합니다.");
    String text = value.strip().toUpperCase(Locale.ROOT);
    if (text.equals("JK") || text.equals("JOKER")) {
      return Joker.INSTANCE;
    }
    if (text.length() < 2) {
      throw new IllegalArgumentException(
          "카드는 SA, D10, HQ, JK 형식으로 입력하세요."
      );
    }
    Suit suit = Suit.parse(text.charAt(0));
    Rank rank = Rank.parse(text.substring(1));
    return new StandardCard(suit, rank);
  }

  public static List<Card> parseList(String value) {
    Objects.requireNonNull(value, "카드 목록 입력이 필요합니다.");
    if (value.isBlank()) {
      return List.of();
    }
    List<Card> cards = Arrays.stream(value.strip().split("[\\s,;/]+"))
        .map(CardParser::parse)
        .toList();
    if (new HashSet<>(cards).size() != cards.size()) {
      throw new IllegalArgumentException("중복 카드가 있습니다.");
    }
    return cards;
  }

}
