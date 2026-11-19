package mighty.model;

import java.util.Objects;

/**
 * 카드 한 장. 일반 카드는 문양과 2~14의 숫자, 조커는 문양 없음(null)과 0으로 표현한다.
 */
public record StandardCard(Suit suit, Rank rank) implements Card {

  /**
   * 문양 없는 일반 카드나 문양이 있는 조커 등 잘못된 조합을 막는다.
   */
  public StandardCard {
    Objects.requireNonNull(suit, "문양이 필요합니다.");
    Objects.requireNonNull(rank, "카드 숫자가 필요합니다.");
  }

  /**
   * 10·J·Q·K·A는 각각 1점이며 조커와 나머지는 0점이다.
   */
  @Override
  public int points() {
    return rank.value() >= Rank.TEN.value() ? 1 : 0;
  }

  /**
   * 화면에서는 문양 기호를 사용한다.
   */
  @Override
  public String displayName() {
    return suit.symbol() + rank.code();
  }

  @Override
  public String toCode() {
    return suit.code() + rank.code();
  }

  /**
   * 과거 대국 기록과 호환되는 SA·D10·JK 표기를 유지한다.
   */
  @Override
  public String toString() {
    return toCode();
  }

}
