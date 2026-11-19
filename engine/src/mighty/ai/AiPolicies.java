package mighty.ai;

import java.util.Objects;
import mighty.ai.bidding.RuleBiddingPolicy;
import mighty.ai.play.RulePlayPolicy;
import mighty.ai.preparation.RuleDiscardPolicy;
import mighty.ai.preparation.PreparationPlanner;
import mighty.ai.preparation.RuleFriendPolicy;

/**
 * 로컬 화면·자동 대국·테탑 연결이 공유하는 현재 전략 구성입니다.
 */
public record AiPolicies(
    RuleBiddingPolicy bidding,
    RuleDiscardPolicy discard,
    RuleFriendPolicy friend,
    RulePlayPolicy play
) {

  public AiPolicies {
    Objects.requireNonNull(bidding);
    Objects.requireNonNull(discard);
    Objects.requireNonNull(friend);
    Objects.requireNonNull(play);
  }

  /**
   * 실행 환경별로 상태를 공유하지 않고 동일한 정책 구성을 생성합니다.
   */
  public static AiPolicies current() {
    return new AiPolicies(new RuleBiddingPolicy(), new RuleDiscardPolicy(),
        new RuleFriendPolicy(), new RulePlayPolicy());
  }

  /**
   * 로컬과 테탑이 함께 사용하는 준비 계획입니다.
   */
  public PreparationPlanner preparation() {
    return new PreparationPlanner();
  }

}
