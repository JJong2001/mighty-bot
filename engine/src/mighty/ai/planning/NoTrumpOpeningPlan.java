package mighty.ai.planning;

import java.util.Objects;
import java.util.Optional;
import mighty.game.friend.FriendSelection;
import mighty.model.StandardCard;
import mighty.model.Suit;

/**
 * 프렌드 평가와 실제 초구가 공유하는 지원 요청 계획입니다.
 */
public record NoTrumpOpeningPlan(
    FriendSelection selection,
    Optional<StandardCard> opening,
    Optional<Suit> returnSuit,
    boolean finalHand,
    boolean connectionBeforeRun,
    Purpose purpose
) {

  /**
   * A의 선사용과 지원 요청을 서로 다른 운영 목적으로 보존합니다.
   */
  public enum Purpose {
    FORCE_JOKER("주력 탑카로 초구를 확보한 뒤 총을 쏘고 독립 회수 문양으로 선을 돌려받습니다."),
    RUN_PRIMARY("주력의 자력 탑카부터 소모하고 마이티 지원은 사이드 손실 처리에 남깁니다."),
    REQUEST_FRIEND("지정 프렌드를 먼저 찾아 회수 카드와 다른 문양의 운영 자원을 남깁니다."),
    OPENING_FRIEND("공통 초구 계획의 반환 문양에서 초구 프렌드를 찾습니다."),
    PROMOTE_PARTNER("풀노의 추가 승리를 열도록 사이드 A로 프렌드의 다음 상위를 승격시킵니다."),
    SECURE_FIRST_TRICK("초구에 이길 수 없는 조커 프렌드를 다음 구에 찾도록 A로 선을 확보합니다."),
    DEFER("공개 진행에 따라 이후 지원 시점을 판단합니다.");

    private final String explanation;

    Purpose(String explanation) {
      this.explanation = explanation;
    }

    public String explanation() {
      return explanation;
    }
  }

  public NoTrumpOpeningPlan {
    Objects.requireNonNull(selection);
    Objects.requireNonNull(opening);
    Objects.requireNonNull(returnSuit);
    Objects.requireNonNull(purpose);
    if (connectionBeforeRun && opening.isEmpty()) {
      throw new IllegalArgumentException("상위 연결을 먼저 요청할 초구가 필요합니다.");
    }
  }
}
