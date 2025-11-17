package com.heungbuja.admin.service;

import com.heungbuja.admin.dto.ActionPerformanceResponse;
import com.heungbuja.admin.dto.ActivityTrendResponse;
import com.heungbuja.admin.dto.UserGameStatsResponse;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.game.domain.GameDetail;
import com.heungbuja.game.entity.Action;
import com.heungbuja.game.entity.GameResult;
import com.heungbuja.game.entity.ScoreByAction;
import com.heungbuja.game.enums.GameSessionStatus;
import com.heungbuja.game.repository.jpa.ActionRepository;
import com.heungbuja.game.repository.jpa.GameResultRepository;
import com.heungbuja.game.repository.mongo.GameDetailRepository;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 사용자 건강 모니터링 서비스
 * 관리자 페이지에서 어르신들의 게임 데이터를 분석하여 건강 상태를 모니터링합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserHealthMonitoringService {

    private final GameResultRepository gameResultRepository;
    private final GameDetailRepository gameDetailRepository;
    private final ActionRepository actionRepository;
    private final UserRepository userRepository;

    /**
     * 사용자별 게임 통계 조회
     */
    public UserGameStatsResponse getUserGameStats(Long userId) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 총 게임 횟수
        Long totalGames = gameResultRepository.countByUser_Id(userId);

        // 완료된 게임 횟수
        Long completedGames = gameResultRepository.countByUser_IdAndStatus(userId, GameSessionStatus.COMPLETED);

        // 최근 게임 기록 조회 (최대 5개)
        List<GameResult> recentGameResults = gameResultRepository.findRecentGamesByUserId(userId);
        List<GameResult> top5Games = recentGameResults.stream().limit(5).collect(Collectors.toList());

        // 평균 점수 계산
        Double avgVerse1 = recentGameResults.stream()
                .map(GameResult::getVerse1AvgScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        Double avgVerse2 = recentGameResults.stream()
                .map(GameResult::getVerse2AvgScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        Double overallAvg = (avgVerse1 + avgVerse2) / 2.0;

        // MongoDB에서 상세 데이터 조회하여 PERFECT/GOOD/BAD 비율 계산
        List<String> sessionIds = recentGameResults.stream()
                .map(GameResult::getSessionId)
                .collect(Collectors.toList());

        List<GameDetail> gameDetails = gameDetailRepository.findBySessionIdIn(sessionIds);

        // PERFECT(3점), GOOD(2점), BAD(1점) 카운트
        int perfectCount = 0;
        int goodCount = 0;
        int badCount = 0;

        for (GameDetail detail : gameDetails) {
            if (detail.getVerse1Stats() != null) {
                perfectCount += detail.getVerse1Stats().getPerfectCount();
                goodCount += detail.getVerse1Stats().getGoodCount();
                badCount += detail.getVerse1Stats().getBadCount();
            }
            if (detail.getVerse2Stats() != null) {
                perfectCount += detail.getVerse2Stats().getPerfectCount();
                goodCount += detail.getVerse2Stats().getGoodCount();
                badCount += detail.getVerse2Stats().getBadCount();
            }
        }

        int totalMovements = perfectCount + goodCount + badCount;
        Double perfectRate = totalMovements > 0 ? (perfectCount * 100.0 / totalMovements) : 0.0;
        Double goodRate = totalMovements > 0 ? (goodCount * 100.0 / totalMovements) : 0.0;
        Double badRate = totalMovements > 0 ? (badCount * 100.0 / totalMovements) : 0.0;

        // 최근 게임 정보 변환
        List<UserGameStatsResponse.RecentGameInfo> recentGames = top5Games.stream()
                .map(gr -> UserGameStatsResponse.RecentGameInfo.builder()
                        .sessionId(gr.getSessionId())
                        .songTitle(gr.getSong() != null ? gr.getSong().getTitle() : "알 수 없음")
                        .verse1Score(gr.getVerse1AvgScore())
                        .verse2Score(gr.getVerse2AvgScore())
                        .status(gr.getStatus().name())
                        .playedAt(gr.getStartTime())
                        .build())
                .collect(Collectors.toList());

        // 마지막 게임 일시
        LocalDateTime lastPlayedAt = recentGameResults.isEmpty() ? null : recentGameResults.get(0).getStartTime();

        return UserGameStatsResponse.builder()
                .userId(userId)
                .userName(user.getName())
                .totalGames(totalGames)
                .completedGames(completedGames)
                .averageVerse1Score(avgVerse1)
                .averageVerse2Score(avgVerse2)
                .overallAverageScore(overallAvg)
                .perfectRate(perfectRate)
                .goodRate(goodRate)
                .badRate(badRate)
                .recentGames(recentGames)
                .lastPlayedAt(lastPlayedAt)
                .build();
    }

    /**
     * 동작별 수행도 분석
     */
    public ActionPerformanceResponse getActionPerformance(Long userId) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 사용자의 모든 게임 결과와 ScoreByAction 조회
        List<GameResult> gameResults = gameResultRepository.findByUser_IdWithScores(userId);

        // 동작별 점수 집계
        Map<Integer, List<Double>> actionScoreMap = new HashMap<>();
        for (GameResult gr : gameResults) {
            for (ScoreByAction sba : gr.getScoresByAction()) {
                actionScoreMap
                        .computeIfAbsent(sba.getActionCode(), k -> new ArrayList<>())
                        .add(sba.getAverageScore());
            }
        }

        // 모든 Action 정보 조회
        List<Action> allActions = actionRepository.findAll();
        Map<Integer, Action> actionMap = allActions.stream()
                .collect(Collectors.toMap(Action::getActionCode, a -> a));

        // MongoDB에서 상세 데이터로 성공률 계산
        List<String> sessionIds = gameResults.stream()
                .map(GameResult::getSessionId)
                .collect(Collectors.toList());
        List<GameDetail> gameDetails = gameDetailRepository.findBySessionIdIn(sessionIds);

        // 동작별 정답/오답 카운트
        Map<String, Integer> correctCountMap = new HashMap<>();
        Map<String, Integer> totalCountMap = new HashMap<>();

        for (GameDetail detail : gameDetails) {
            if (detail.getVerse1Movements() != null) {
                for (GameDetail.Movement movement : detail.getVerse1Movements()) {
                    String action = movement.getAction();
                    totalCountMap.put(action, totalCountMap.getOrDefault(action, 0) + 1);
                    if (movement.isCorrect()) {
                        correctCountMap.put(action, correctCountMap.getOrDefault(action, 0) + 1);
                    }
                }
            }
            if (detail.getVerse2Movements() != null) {
                for (GameDetail.Movement movement : detail.getVerse2Movements()) {
                    String action = movement.getAction();
                    totalCountMap.put(action, totalCountMap.getOrDefault(action, 0) + 1);
                    if (movement.isCorrect()) {
                        correctCountMap.put(action, correctCountMap.getOrDefault(action, 0) + 1);
                    }
                }
            }
        }

        // ActionScore 리스트 생성
        List<ActionPerformanceResponse.ActionScore> actionScores = new ArrayList<>();
        for (Map.Entry<Integer, List<Double>> entry : actionScoreMap.entrySet()) {
            Integer actionCode = entry.getKey();
            List<Double> scores = entry.getValue();

            Double avgScore = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            Long attemptCount = (long) scores.size();

            Action action = actionMap.get(actionCode);
            String actionName = action != null ? action.getName() : "동작" + actionCode;
            String actionDescription = action != null ? action.getDescription() : "";

            // 성공률 계산 (actionCode를 문자열로 변환하여 매칭)
            String actionCodeStr = String.valueOf(actionCode);
            Integer totalAttempts = totalCountMap.getOrDefault(actionCodeStr, 0);
            Integer correctAttempts = correctCountMap.getOrDefault(actionCodeStr, 0);
            Double successRate = totalAttempts > 0 ? (correctAttempts * 100.0 / totalAttempts) : 0.0;

            actionScores.add(ActionPerformanceResponse.ActionScore.builder()
                    .actionCode(actionCode)
                    .actionName(actionName)
                    .actionDescription(actionDescription)
                    .averageScore(avgScore)
                    .attemptCount(attemptCount)
                    .successRate(successRate)
                    .build());
        }

        // 점수 순으로 정렬
        actionScores.sort(Comparator.comparing(ActionPerformanceResponse.ActionScore::getAverageScore).reversed());

        // TOP 3 (잘하는 동작)
        List<ActionPerformanceResponse.ActionScore> topActions = actionScores.stream()
                .limit(3)
                .collect(Collectors.toList());

        // 약점 동작 (점수 낮은 순, 최대 3개)
        List<ActionPerformanceResponse.ActionScore> weakActions = actionScores.stream()
                .sorted(Comparator.comparing(ActionPerformanceResponse.ActionScore::getAverageScore))
                .limit(3)
                .collect(Collectors.toList());

        return ActionPerformanceResponse.builder()
                .userId(userId)
                .userName(user.getName())
                .actionScores(actionScores)
                .topActions(topActions)
                .weakActions(weakActions)
                .build();
    }

    /**
     * 시간대별 활동 추이 분석
     */
    public ActivityTrendResponse getActivityTrend(Long userId, Integer periodDays) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 기간 설정 (기본값: 7일)
        if (periodDays == null || periodDays <= 0) {
            periodDays = 7;
        }

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(periodDays);

        // 기간 내 게임 기록 조회
        List<GameResult> gameResults = gameResultRepository.findByUser_IdAndStartTimeBetween(userId, startDate, endDate);

        // 일별 그룹화
        Map<LocalDate, List<GameResult>> dailyGameMap = gameResults.stream()
                .collect(Collectors.groupingBy(gr -> gr.getStartTime().toLocalDate()));

        // 일별 활동 데이터 생성
        List<ActivityTrendResponse.DailyActivity> dailyActivities = new ArrayList<>();
        for (int i = 0; i < periodDays; i++) {
            LocalDate date = LocalDate.now().minusDays(periodDays - 1 - i);
            List<GameResult> gamesOnDate = dailyGameMap.getOrDefault(date, new ArrayList<>());

            Long gameCount = (long) gamesOnDate.size();

            Double avgScore = gamesOnDate.stream()
                    .map(gr -> {
                        Double v1 = gr.getVerse1AvgScore();
                        Double v2 = gr.getVerse2AvgScore();
                        if (v1 != null && v2 != null) return (v1 + v2) / 2.0;
                        if (v1 != null) return v1;
                        if (v2 != null) return v2;
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            // 플레이 시간 계산 (분 단위)
            Long totalPlayTimeMinutes = gamesOnDate.stream()
                    .map(gr -> {
                        if (gr.getStartTime() != null && gr.getEndTime() != null) {
                            return java.time.Duration.between(gr.getStartTime(), gr.getEndTime()).toMinutes();
                        }
                        return 0L;
                    })
                    .mapToLong(Long::longValue)
                    .sum();

            String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);

            dailyActivities.add(ActivityTrendResponse.DailyActivity.builder()
                    .date(date)
                    .dayOfWeek(dayOfWeek)
                    .gameCount(gameCount)
                    .averageScore(avgScore)
                    .totalPlayTimeMinutes(totalPlayTimeMinutes)
                    .build());
        }

        // 총 게임 횟수
        Long totalGames = (long) gameResults.size();

        // 평균 일일 게임 횟수
        Double averageDailyGames = totalGames * 1.0 / periodDays;

        // 활동량 추세 분석 (최근 절반 vs 이전 절반)
        int halfPeriod = periodDays / 2;
        long recentHalfGames = dailyActivities.stream().skip(halfPeriod).mapToLong(ActivityTrendResponse.DailyActivity::getGameCount).sum();
        long previousHalfGames = dailyActivities.stream().limit(halfPeriod).mapToLong(ActivityTrendResponse.DailyActivity::getGameCount).sum();

        String trend;
        if (recentHalfGames > previousHalfGames * 1.2) {
            trend = "INCREASING";
        } else if (recentHalfGames < previousHalfGames * 0.8) {
            trend = "DECREASING";
        } else {
            trend = "STABLE";
        }

        // 가장 활발한 요일 찾기
        Map<DayOfWeek, Long> dayOfWeekCountMap = gameResults.stream()
                .collect(Collectors.groupingBy(gr -> gr.getStartTime().getDayOfWeek(), Collectors.counting()));

        String mostActiveDayOfWeek = dayOfWeekCountMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey().getDisplayName(TextStyle.FULL, Locale.KOREAN))
                .orElse("없음");

        return ActivityTrendResponse.builder()
                .userId(userId)
                .userName(user.getName())
                .periodDays(periodDays)
                .dailyActivities(dailyActivities)
                .totalGames(totalGames)
                .averageDailyGames(averageDailyGames)
                .trend(trend)
                .mostActiveDayOfWeek(mostActiveDayOfWeek)
                .build();
    }
}
