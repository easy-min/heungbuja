package com.heungbuja.game.strategy;

import org.springframework.stereotype.Component;
import java.util.List;

@Component("simpleScoring") // 이 채점 로직의 별명을 "simpleScoring"으로 지정
public class SimpleScoringStrategy implements ScoringStrategy {

    @Override
    public double grade(List<Integer> userActionCodes, List<Integer> correctActionCodes) {
        if (correctActionCodes == null || correctActionCodes.isEmpty()) {
            return 0.0;
        }

        int correctCount = 0;
        int totalActions = correctActionCodes.size();
        for (int i = 0; i < totalActions; i++) {
            if (i < userActionCodes.size() && correctActionCodes.get(i).equals(userActionCodes.get(i))) {
                correctCount++;
            }
        }

        return (double) correctCount / totalActions * 100.0;
    }
}