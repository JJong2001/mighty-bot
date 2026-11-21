package mighty.bridge;

import java.util.*;
import java.util.stream.IntStream;
import mighty.ai.AiPolicies;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.action.*;
import mighty.game.bidding.*;
import mighty.game.deal.*;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.model.*;

/**
 * 동일 관측에서 실제 테탑 변환과 로컬 공통 정책의 선택을 비교하는 연결 회귀 검사입니다.
 */
public final class PolicyParityCheck {

  private static final AiPolicies LOCAL = AiPolicies.current();

  private static List<Card> cards(String text) {
    return CardParser.parseList(text);
  }

  private static void require(boolean value) {
    if (!value) {
      throw new AssertionError("로컬과 테탑 정책의 선택이 다릅니다.");
    }
  }

  private static Map<String, Object> request(String phase, String hand) {
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("requestId", "parity");
    req.put("phase", phase);
    req.put("self", "Red");
    req.put("hand", cards(hand).stream().map(Card::toCode).toList());
    req.put("order", List.of("Red", "Blue", "White", "Green", "Yellow"));
    req.put("active", req.get("order"));
    req.put("score", 40);
    req.put("contract", Map.of("points", 20, "trump", "N"));
    return req;
  }

  public static void main(String[] args) {
    verifyNoTrumpConnectionPlan();
    var contested = request("before", "JK SK S5 S3 D10 HA HK HQ H2 CK");
    contested.put("contract", Map.of("points", 17, "trump", "N"));
    var suited = BotBridge.choose(contested);
    require(suited.get("points").equals(18) && suited.get("trump").equals("H"));
    var support = request("friend", "SA SK S5 S3 HA HK HQ H2 JK C2");
    support.put("contract", Map.of("points", 17, "trump", "N"));
    support.put("discarded", List.of("D10", "D2", "D3"));
    require(BotBridge.choose(support).get("friend").equals("SQ"));
    support.put("auction",
        List.of(Map.of("color", "Blue", "type", "bid", "points", 16, "trump", "C")));
    require(BotBridge.choose(support).get("friend").equals("CA"));
    var before = request("before", "SA SJ S8 S3 DJ D5 D4 D2 H10 C4");
    before.put("contract", Map.of("points", 15, "trump", "N"));
    var preKitty = BotBridge.choose(before);
    require(preKitty.get("type").equals("contract") && preKitty.get("points").equals(16)
        && preKitty.get("trump").equals("D"));
    before.put("contract", Map.of("points", 16, "trump", "N"));
    var sixNoTrump = BotBridge.choose(before);
    require(sixNoTrump.get("points").equals(16) && sixNoTrump.get("trump").equals("N"));
    String hand = "SK SQ SJ S7 S6 S2 DA CA C2 JK";
    String buried = "H9 H8 H4";
    Bid contract = new Bid(20, Trump.NO_TRUMP);
    var req = request("discard", hand + " " + buried);
    Set<String> expectedDiscards = new HashSet<>(LOCAL.preparation().choose(
            new DiscardView(new Seat(0), contract, cards(hand + " " + buried))).discarded()
        .stream().map(Card::toCode).toList());
    require(expectedDiscards.equals(new HashSet<>((List<?>) BotBridge.choose(req).get("cards"))));
    req = request("friend", hand);
    req.put("discarded", cards(buried).stream().map(Card::toCode).toList());
    FriendSelection selected = LOCAL.friend()
        .choose(new FriendView(new Seat(0), contract, cards(hand), cards(buried)));
    require(selected instanceof CardFriend cf && cf.card().toCode()
        .equals(BotBridge.choose(req).get("friend")));
    List<Seat> seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats));
    req = request("play", hand);
    req.put("discarded", cards(buried).stream().map(Card::toCode).toList());
    req.put("declarer", "Red");
    req.put("friend", "SA");
    req.put("history", List.of());
    req.put("trick", List.of());
    View view = ObservationFactory.create(new Seat(0), new DeclarerInfo(cards(hand), cards(buried)),
        contract,
        new CardFriend(CardParser.parse("SA")),
        new Context(order, new Seat(0), UnrevealedFriend.INSTANCE, List.of()),
        List.of());
    String localCard = LOCAL.play().choose(view).card().toCode();
    require(localCard.equals("C2"));
    require(localCard.equals(BotBridge.choose(req).get("card")));
    String bidHand = "SA SK SJ S7 S2 DQ D10 H5 C10 C5";
    req = request("bidding", bidHand);
    req.put("self", "Blue");
    req.put("opening", "Red");
    req.put("auction", List.of(Map.of("color", "Red", "type", "bid", "points", 15, "trump", "H")));
    var bidding = BiddingState.start(new DealParticipants(DealFormat.FIVE_PLAYER_OPENING, seats),
            new Seat(0))
        .submit(new PublicBid(new Seat(0), new BidOffer(new Bid(15, Trump.HEART))));
    var localBid = LOCAL.bidding()
        .choose(new BiddingView(new Seat(1), cards(bidHand), 40, bidding));
    var response = BotBridge.choose(req);
    require(localBid instanceof BidOffer offer && offer.bid().equals(new Bid(15, Trump.NO_TRUMP)));
    require(response.get("type").equals("bid") && response.get("points").equals(15) && response.get(
        "trump").equals("N"));
    // 공개 공약 변경을 보낸 경우와 누락한 경우를 구분합니다.
    req = request("play", "SK SQ HK H5 C10 C6 D2 D3 D4");
    req.put("self", "Blue");
    req.put("declarer", "Red");
    req.put("friend", "SA");
    req.put("publicFriend", "Blue");
    List<Map<String, Object>> row = new ArrayList<>();
    String[] colors = {
        "Red", "Blue", "White", "Green", "Yellow"
    };
    String[] codes = {
        "C2", "SA", "C3", "C4", "C5"
    };
    for (int index = 0; index < 5; index++) {
      row.add(Map.of("color", colors[index], "card", codes[index]));
    }
    req.put("history", List.of(Map.of("number", 1, "winner", "Blue", "plays", row)));
    req.put("trick", List.of());
    req.put("contractHistory",
        List.of(Map.of("points", 20, "trump", "H"), Map.of("points", 20, "trump", "N")));
    require(BotBridge.choose(req).get("card").equals("SK"));
    String prepared = "D3 D6 D7 D8 D9 DJ DQ C6 CA JK";
    req = request("after", prepared);
    req.put("contract", Map.of("points", 16, "trump", "D"));
    req.put("contractHistory", List.of(Map.of("points", 16, "trump", "D")));
    req.put("discarded", cards("H2 H4 S2").stream().map(Card::toCode).toList());
    var finalBid = LOCAL.preparation().finalContract(new FriendView(new Seat(0),
            new Bid(16, Trump.DIAMOND), cards(prepared), cards("H2 H4 S2")),
        new Bid(16, Trump.DIAMOND));
    var finalResponse = BotBridge.choose(req);
    require(finalBid.equals(new Bid(17, Trump.NO_TRUMP)));
    require(finalResponse.get("type").equals("contract") && finalResponse.get("points").equals(17)
        && finalResponse.get("trump").equals("N"));
    req = request("bidding", "SA S2 D2 D3 D4 H2 H3 H4 C2 C3");
    req.put("opening", "Red");
    req.put("auction", List.of());
    require(BotBridge.choose(req).get("type").equals("dealMiss"));
    req = request("bidding", "SA S2 D2 D3 H2 H3 C2 C3");
    req.put("opening", "Red");
    req.put("auction", List.of());
    req.put("format", "six-opening");
    req.put("order", List.of("Red", "Blue", "Purple", "White", "Green", "Yellow"));
    req.put("active", req.get("order"));
    require(!BotBridge.choose(req).get("type").equals("dealMiss"));
    req = request("bidding", "SA S2 D2 D3 D4 H2 H3 H4 C2 C3");
    req.put("opening", "Red");
    req.put("auction", List.of());
    req.put("format", "five");
    req.put("order", List.of("Red", "Blue", "Purple", "White", "Green", "Yellow"));
    req.put("active", List.of("Red", "Blue", "White", "Green", "Yellow"));
    require(!BotBridge.choose(req).get("type").equals("dealMiss"));
    // 새 운영 평가가 테탑 변환 뒤에도 같은 공약·프렌드를 선택하는지 확인합니다.
    String[] planHands = {
        "H2 H3 H4 H6 H7 HJ HK HA D6 SA", "SJ SQ SK SA D8 DJ DQ DK CK CA"
    };
    int[] expectedPoints = {
        20, 17
    };
    for (int index = 0; index < planHands.length; index++) {
      req = request("after", planHands[index]);
      req.put("contract", Map.of("points", 16, "trump", index == 0 ? "H" : "D"));
      req.put("contractHistory", List.of(req.get("contract")));
      req.put("discarded", cards("C2 H5 H8").stream().map(Card::toCode).toList());
      var updated = BotBridge.choose(req);
      require(
          updated.get("points").equals(expectedPoints[index]) && updated.get("trump").equals("N"));
    }
    req = request("friend", planHands[1]);
    req.put("contract", Map.of("points", 17, "trump", "N"));
    req.put("discarded", cards("C2 H5 H8").stream().map(Card::toCode).toList());
    require(BotBridge.choose(req).get("friend").equals("DA"));
    req = request("play", "SA CA D6 D3 H5 HQ D10 DJ C2 HJ");
    req.put("self", "Blue");
    req.put("declarer", "Red");
    req.put("friend", "SA");
    req.put("history", List.of());
    req.put("trick", List.of(Map.of("color", "Red", "card", "C5")));
    require(BotBridge.choose(req).get("card").equals("SA"));
    req = request("play", "SQ S2 H2 H3 H4 D2 D3 D4 C2 C3");
    req.put("contract", Map.of("points", 17, "trump", "H"));
    req.put("self", "Blue");
    req.put("declarer", "Red");
    req.put("friend", "JK");
    req.put("history", List.of());
    req.put("trick", List.of(Map.of("color", "Red", "card", "SK")));
    require(BotBridge.choose(req).get("card").equals("S2"));
    req = request("after", "S4 SJ SQ SK C3 C4 C8 C9 CK CA");
    req.put("contract", Map.of("points", 16, "trump", "C"));
    req.put("contractHistory", List.of(req.get("contract")));
    req.put("discarded", cards("D2 D3 D4").stream().map(Card::toCode).toList());
    var gunRoute = BotBridge.choose(req);
    require(
        gunRoute.get("trump").equals("N") && ((Number) gunRoute.get("points")).intValue() >= 17);
    req = request("play", "HK H2 JK D2 D3 C2 C3 S2 S3 S4");
    req.put("contract", Map.of("points", 17, "trump", "N"));
    req.put("self", "Blue");
    req.put("declarer", "Red");
    req.put("friend", "JK");
    req.put("history", List.of());
    req.put("trick", List.of(Map.of("color", "Red", "card", "HA")));
    require(BotBridge.choose(req).get("card").equals("H2"));
    req = request("play", "HQ H2 C2 C3 C4 S2 S3 S4 D2 D3");
    req.put("contract", Map.of("points", 16, "trump", "D"));
    req.put("self", "Blue");
    req.put("declarer", "Red");
    req.put("friend", "HA");
    req.put("history", List.of());
    req.put("trick", List.of(Map.of("color", "Red", "card", "HK")));
    require(BotBridge.choose(req).get("card").equals("H2"));
    List<Map<String, Object>> supportHistory = new ArrayList<>();
    String[][] supportCards = {
        {
            "DA", "D2", "D3", "D4", "D5"
        }, {
        "H2", "SA", "H5", "H6", "HA"
    }
    };
    for (int index = 0; index < 2; index++) {
      List<Map<String, Object>> plays = new ArrayList<>();
      for (int seat = 0; seat < 5; seat++) {
        plays.add(Map.of("color", colors[seat], "card", supportCards[index][seat]));
      }
      supportHistory.add(
          Map.of("number", index + 1, "winner", index == 0 ? "Red" : "Blue", "plays", plays));
    }
    req = request("play", "D6 H10 HJ S2 S3 C2 C3 C4");
    req.put("contract", Map.of("points", 17, "trump", "N"));
    req.put("self", "Blue");
    req.put("declarer", "Red");
    req.put("friend", "SA");
    req.put("publicFriend", "Blue");
    req.put("history", supportHistory);
    req.put("trick", List.of());
    require(BotBridge.choose(req).get("card").equals("HJ"));
    req = request("play", "SK D3 D6 DK C6 C7 CQ CK CA JK");
    req.put("contract", Map.of("points", 15, "trump", "N"));
    req.put("declarer", "Red");
    req.put("friend", "SA");
    req.put("discarded", cards("H2 H3 H4").stream().map(Card::toCode).toList());
    req.put("history", List.of());
    req.put("trick", List.of());
    require(Set.of("D3", "CA").contains(BotBridge.choose(req).get("card")));
    req = request("play", "SA HA H2 D2 D3 D4 C2 C3 S2 S3");
    req.put("contract", Map.of("points", 16, "trump", "D"));
    req.put("self", "Blue");
    req.put("declarer", "Red");
    req.put("friend", "SA");
    req.put("history", List.of());
    req.put("trick", List.of(Map.of("color", "Red", "card", "H3")));
    require(BotBridge.choose(req).get("card").equals("HA"));
    req = request("play", "DQ D3 D4 C2 C3 C4 S2 S3 H2 H3");
    req.put("contract", Map.of("points", 16, "trump", "H"));
    req.put("self", "White");
    req.put("declarer", "Red");
    req.put("friend", "SA");
    req.put("history", List.of());
    req.put("trick",
        List.of(Map.of("color", "Red", "card", "D2"), Map.of("color", "Blue", "card", "DJ")));
    require(BotBridge.choose(req).get("card").equals("D3"));
    req = request("play", "HA H3 H4 D2 D3 D4 S2 S3 C6");
    req.put("contract", Map.of("points", 16, "trump", "H"));
    req.put("self", "Green");
    req.put("declarer", "White");
    req.put("friend", "SA");
    req.put("publicFriend", "Red");
    req.put("history", List.of(Map.of("number", 1, "winner", "Red", "plays", List.of(
        Map.of("color", "White", "card", "C2"), Map.of("color", "Green", "card", "C3"),
        Map.of("color", "Yellow", "card", "C4"), Map.of("color", "Red", "card", "SA"),
        Map.of("color", "Blue",
            "card", "C5")))));
    req.put("trick",
        List.of(Map.of("color", "Red", "card", "H10"), Map.of("color", "Blue", "card", "H2"),
            Map.of("color", "White", "card", "HK")));
    require(BotBridge.choose(req).get("card").equals("HA"));
    // 새 기루 요청은 여러 상위가 남았다는 이유로 조커를 숨기지 않습니다.
    req = request("play", "JK D2 D3 D4 D5 H2 H6 C6 S2");
    req.put("contract", Map.of("points", 16, "trump", "D"));
    req.put("self", "Blue");
    req.put("declarer", "Red");
    req.put("friend", "JK");
    req.put("history", List.of(Map.of("number", 1, "winner", "Red", "plays", List.of(
        Map.of("color", "Red", "card", "CA"), Map.of("color", "Blue", "card", "C2"),
        Map.of("color", "White", "card", "C3"), Map.of("color", "Green", "card", "C4"),
        Map.of("color", "Yellow", "card", "C5")))));
    req.put("trick", List.of(Map.of("color", "Red", "card", "DJ")));
    require(BotBridge.choose(req).get("card").equals("JK"));
    // 풀다리 → 풀노의 프렌드는 T부터 주력을 반환합니다.
    req = request("play", "D2 D4 D10 C2 C3 C4 H2 H3 S6");
    req.put("contract", Map.of("points", 20, "trump", "N"));
    req.put("contractHistory",
        List.of(Map.of("points", 20, "trump", "D"), Map.of("points", 20, "trump", "N")));
    req.put("self", "Blue");
    req.put("declarer", "Red");
    req.put("friend", "SK");
    req.put("publicFriend", "Blue");
    req.put("history", List.of(Map.of("number", 1, "winner", "Blue", "plays", List.of(
        Map.of("color", "Red", "card", "S2"), Map.of("color", "Blue", "card", "SK"),
        Map.of("color", "White", "card", "S3"), Map.of("color", "Green", "card", "S4"),
        Map.of("color", "Yellow", "card", "S5")))));
    req.put("trick", List.of());
    require(BotBridge.choose(req).get("card").equals("D10"));
    System.out.println(
        "PASS: shared bidding/discard/friend/play policies, current declarer opening, public contract history");
  }

  /**
   * 같은 공개 공약에서 프렌드 지정과 초구가 공통 계획을 따르는지 확인합니다.
   */
  private static void verifyNoTrumpConnectionPlan() {
    String hand = "S7 SA D2 DK C3 C4 CQ CK CA JK";
    String buried = "H3 H4 HQ";
    var wireBids = List.of(Map.of("points", 20, "trump", "C"),
        Map.of("points", 20, "trump", "N"));
    var bids = List.of(new Bid(20, Trump.CLUB), new Bid(20, Trump.NO_TRUMP));
    var req = request("friend", hand);
    req.put("discarded", cards(buried).stream().map(Card::toCode).toList());
    req.put("contractHistory", wireBids);
    var local = LOCAL.friend().choose(new FriendView(new Seat(0), bids.get(1), cards(hand),
        cards(buried), bids));
    require(local.equals(new CardFriend(CardParser.parse("DA"))));
    require(BotBridge.choose(req).get("friend").equals("DA"));
    req.put("phase", "play");
    req.put("declarer", "Red");
    req.put("friend", "DA");
    req.put("history", List.of());
    req.put("trick", List.of());
    require(BotBridge.choose(req).get("card").equals("D2"));
  }

}
