package com.heungbuja.game.factory;

import com.heungbuja.game.strategy.ScoringStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScoringStrategyFactory {

    // Spring이 @Component로 등록된 모든 ScoringStrategy 구현체를 여기에 주입해줍니다.
    private final Map<String, ScoringStrategy> strategies;

    /**
     * 난이도나 게임 모드에 따라 적절한 채점 전략을 반환합니다.
     * @param verse 현재 절 (1 또는 2)
     * @param difficulty 난이도 (1, 2, 3)
     * @return 채점 전략 객체
     */
    public ScoringStrategy createStrategy(int verse, Integer difficulty) {
        // 현재는 채점 방식이 하나뿐이므로 항상 SimpleScoringStrategy를 반환합니다.
        // TODO: 나중에 2절의 난이도별로 다른 채점 방식이 생긴다면,
        // if (verse == 2 && difficulty == 3) {
        //     return strategies.get("bonusScoring"); // 예: "bonusScoring" 전략 반환
        // }
        return strategies.get("simpleScoring");
    }
}