package mighty.game.bidding;

import java.util.Objects;
import mighty.game.GamePhase;
import mighty.model.Trump;

/**
 * 경쟁 비딩 종료 뒤 주공의 공약 변경 규칙입니다.
 */
public final class ContractChangeRules {

  private ContractChangeRules() {
  }

  /**
   * 낙찰 공약, 현재 공약, 제안 및 바닥 확인 시점으로 변경 가능 여부를 판정합니다.
   */
  public static boolean canChange(Bid awarded, Bid current, Bid proposed, GamePhase phase) {
    Objects.requireNonNull(awarded);
    Objects.requireNonNull(current);
    Objects.requireNonNull(proposed);
    if (phase != GamePhase.BEFORE_KITTY && phase != GamePhase.DISCARD
        && phase != GamePhase.FRIEND) {
      return false;
    }
    if (current.equals(proposed)) {
      return true;
    }
    if (current.points() == Bid.MAX_POINTS && proposed.points() == Bid.MAX_POINTS) {
      return true;
    }
    if (phase == GamePhase.BEFORE_KITTY && current.trump() != Trump.NO_TRUMP
        && proposed.trump() == Trump.NO_TRUMP) {
      return proposed.points() >= current.points();
    }
    if (proposed.points() <= current.points()) {
      return false;
    }
    // 닷노로 낙찰받은 뒤 곱노 운영이 완성되면 같은 NT의 곱노 인상도 허용합니다.
    // 그 밖의 같은 문양 인상은 기존에 확인된 풀 인상 범위를 유지합니다.
    if (proposed.trump() == current.trump()) {
      return proposed.points() == Bid.MAX_POINTS
          || (current.trump() == Trump.NO_TRUMP && current.points() == 15
          && proposed.points() == 17 && phase != GamePhase.BEFORE_KITTY);
    }
    int increase = phase == GamePhase.BEFORE_KITTY || proposed.trump() == Trump.NO_TRUMP ? 1 : 2;
    return proposed.points() - current.points() >= increase;
  }

}
