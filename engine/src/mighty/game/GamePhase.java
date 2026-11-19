package mighty.game;

public enum GamePhase {
  /**
   * 현재 배분된 손패로 비딩
   */
  BIDDING,
  /**
   * 6마 죽일 카드 지정
   */
  EXCLUSION_SELECTION,
  /**
   * 휴식자 정하고 남은 다섯 명에게 카드 배분
   */
  REDISTRIBUTION,
  /**
   * 다섯 명이 10장씩 가진 상태에서 바닥 확인 준비
   */
  BEFORE_KITTY,
  /**
   * 주공이 바닥을 받은 뒤 버릴 세 장 선택
   */
  DISCARD,
  /**
   * 프렌드 지정
   */
  FRIEND,
  /**
   * 현재 구에서 카드 제출
   */
  PLAY,
  /**
   * 모든 사람의 카드 제출이 끝나고 승자 & 점수 처리
   */
  TRICK_END,
  /**
   * 한 판 종료
   */
  FINISHED
}
