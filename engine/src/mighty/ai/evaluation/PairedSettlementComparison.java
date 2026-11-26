package mighty.ai.evaluation;

import java.util.List;

/**
 * 같은 가상 배분의 정산 차이로 기본 운영을 바꿀 근거가 충분한지 비교합니다.
 */
public final class PairedSettlementComparison {

  // 작은 표본의 우연한 우위를 그대로 채택하지 않는 정책이며 성공 보장은 아닙니다.
  private static final double REQUIRED_STANDARD_ERRORS = 2.0;

  private PairedSettlementComparison() {
  }

  public static boolean hasClearGain(List<Integer> baseline, List<Integer> alternative) {
    int count = baseline.size();
    if (count < 2 || alternative.size() != count) {
      return false;
    }
    double sum = 0;
    double squares = 0;
    for (int i = 0; i < count; i++) {
      double difference = (double) alternative.get(i) - baseline.get(i);
      sum += difference;
      squares += difference * difference;
    }
    double mean = sum / count;
    double error = Math.sqrt(Math.max(0, squares - sum * mean) / (count * (count - 1.0)));
    return mean > REQUIRED_STANDARD_ERRORS * error;
  }
}
