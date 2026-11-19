package mighty.ai.planning.distribution;

/**
 * 미확인 배분별 협력 대국의 결과입니다. 카드별 위험의 합과 구분합니다.
 */
public record NoTrumpDistribution(int samples, int sixteenWins, int seventeenWins, int twentyWins,
                                  double meanPoints,
                                  int eighteenWins, int nineteenWins) {

  public double sixteenRate() {
    return samples == 0 ? 0 : (double) sixteenWins / samples;
  }

  public double seventeenRate() {
    return samples == 0 ? 0 : (double) seventeenWins / samples;
  }

  public double twentyRate() {
    return samples == 0 ? 0 : (double) twentyWins / samples;
  }

}
