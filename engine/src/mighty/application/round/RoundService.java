package mighty.application.round;

import java.util.Objects;
import java.util.Optional;
import mighty.ai.AiPolicies;
import mighty.ai.play.BaselinePlayPolicy;
import mighty.ai.play.PlayPolicy;
import mighty.ai.preparation.BaselineDiscardPolicy;
import mighty.ai.preparation.BaselineFriendPolicy;
import mighty.ai.preparation.DiscardPolicy;
import mighty.ai.preparation.FriendPolicy;
import mighty.game.GameMode;
import mighty.game.GamePhase;
import mighty.game.Seat;
import mighty.game.TrickNumber;
import mighty.game.observation.View;
import mighty.game.round.RoundResult;
import mighty.game.round.RoundState;
import mighty.game.trick.PublicPlay;

/**
 * 전용 관측을 정책에 전달하고, 선택한 행동을 한 판의 상태에 반영합니다.
 */
public final class RoundService {

  private final DiscardPolicy discardPolicy;
  private final FriendPolicy friendPolicy;
  private final PlayPolicy playPolicy;
  private final Optional<RoundPreparation> preparation;

  public RoundService(DiscardPolicy discardPolicy, FriendPolicy friendPolicy,
      PlayPolicy playPolicy) {
    this(discardPolicy, friendPolicy, playPolicy, Optional.empty());
  }

  private RoundService(DiscardPolicy discardPolicy, FriendPolicy friendPolicy,
      PlayPolicy playPolicy,
      Optional<RoundPreparation> preparation) {
    this.discardPolicy = Objects.requireNonNull(discardPolicy, "버리기 정책이 필요합니다.");
    this.friendPolicy = Objects.requireNonNull(friendPolicy, "프렌드 정책이 필요합니다.");
    this.playPolicy = Objects.requireNonNull(playPolicy, "플레이 정책이 필요합니다.");
    this.preparation = Objects.requireNonNull(preparation);
  }

  /**
   * 기본 동작용 정책 세 가지를 연결한 서비스를 생성합니다.
   */
  public static RoundService baseline() {
    return new RoundService(new BaselineDiscardPolicy(), new BaselineFriendPolicy(),
        new BaselinePlayPolicy());
  }

  /**
   * 문서의 지원·보존 규칙을 적용하는 정책 세 가지를 연결합니다.
   */
  public static RoundService strategic() {
    AiPolicies policies = AiPolicies.current();
    return new RoundService(policies.discard(), policies.friend(), policies.play(),
        Optional.of(new RoundPreparation(policies.preparation(), policies.friend())));
  }

  /**
   * 준비 단계 하나 또는 카드 제출 한 차례를 진행합니다.
   */
  public void advance(RoundState round) {
    Objects.requireNonNull(round, "진행할 판이 필요합니다.");
    Seat actor = round.currentSeat().orElseThrow(
        () -> new IllegalStateException("이미 종료된 판입니다."));
    switch (round.phase()) {
      case BEFORE_KITTY -> {
        if (preparation.isPresent()) {
          preparation.orElseThrow().beforeKitty(round,
              bid -> round.reviseContract(actor, bid));
        }
        round.takeKitty(actor);
      }
      case DISCARD -> {
        if (preparation.isPresent()) {
          preparation.orElseThrow().discard(round, bid -> round.reviseContract(actor, bid),
              cards -> round.discard(actor, cards));
        } else {
          round.discard(actor, discardPolicy.choose(round.discardView(actor)));
        }
      }
      case FRIEND -> {
        if (preparation.isPresent()) {
          preparation.orElseThrow().chooseFriend(round, bid -> round.reviseContract(actor, bid),
              selection -> round.chooseFriend(actor, selection));
        } else {
          round.chooseFriend(actor, friendPolicy.choose(round.friendView(actor)));
        }
      }
      case PLAY -> {
        View view = round.currentView();
        if (preparation.isPresent() && mighty.game.setting.SettingProof.canDeclare(view)) {
          round.declareSetting(actor);
        } else {
          round.play(new PublicPlay(actor, playPolicy.choose(view)));
        }
      }
      default -> throw new IllegalStateException("지원하지 않는 진행 단계입니다: " + round.phase());
    }
  }

  /**
   * 현재 단계부터 10구 종료까지 진행하고 카드 득점 결과를 반환합니다.
   */
  public RoundResult playToEnd(RoundState round) {
    Objects.requireNonNull(round, "진행할 판이 필요합니다.");
    int maximumSteps = 3 + GameMode.PLAYING_PLAYER_COUNT * TrickNumber.LAST;
    for (int step = 0; round.phase() != GamePhase.FINISHED; step++) {
      if (step >= maximumSteps) {
        throw new IllegalStateException("한 판의 최대 진행 횟수를 초과했습니다.");
      }
      advance(round);
    }
    return round.result().orElseThrow();
  }

}
