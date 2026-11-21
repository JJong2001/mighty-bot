package mighty.ai.bidding;

import java.util.List;
import java.util.Objects;
import mighty.game.bidding.Bid;
import mighty.model.Trump;

/**
 * 입찰 자격, 기본 공약, 문양별 상한과 후보 선호를 분리한 평가입니다.
 */
public record BidAssessment(
    Trump trump,
    boolean canCompete,
    int openingPoints,
    int ceilingPoints,
    int preference,
    List<String> reasons,
    boolean compareWithSuitedBids
) {

  public BidAssessment(Trump trump, boolean canCompete, int openingPoints, int ceilingPoints,
      int preference, List<String> reasons) {
    this(trump, canCompete, openingPoints, ceilingPoints, preference, reasons, false);
  }

  public BidAssessment {
    Objects.requireNonNull(trump);
    reasons = List.copyOf(reasons);
    if (openingPoints < Bid.MIN_POINTS || ceilingPoints > Bid.MAX_POINTS
        || openingPoints > ceilingPoints) {
      throw new IllegalArgumentException("기본 공약과 상한의 범위가 잘못됐습니다.");
    }
  }

  /**
   * 입찰 불가 후보의 13은 강제 첫 입찰용 표시이며 경쟁 허가가 아닙니다.
   */
  static BidAssessment unavailable(Trump trump, String reason) {
    return new BidAssessment(trump, false, Bid.MIN_POINTS, Bid.MIN_POINTS, 0, List.of(reason));
  }

}
