package com.heungbuja.command.service.impl;

import com.heungbuja.command.dto.IntentResult;
import com.heungbuja.command.service.IntentClassifier;
import com.heungbuja.voice.enums.Intent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 키워드 기반 의도 분석기
 * 간단하고 빠르지만, 복잡한 문장은 처리 어려움
 * 추후 RAG 또는 LLM 기반으로 교체 가능
 */
@Slf4j
@Component
public class KeywordBasedIntentClassifier implements IntentClassifier {

    // 응급 키워드
    private static final List<String> EMERGENCY_KEYWORDS = Arrays.asList(
            "도와줘", "도와주세요", "살려줘", "살려주세요", "아야", "아파", "쓰러졌어", "위험해"
    );
    private static final List<String> EMERGENCY_CANCEL_KEYWORDS = Arrays.asList(
            "괜찮아", "괜찮습니다", "괜찮아요", "괜찮네요", "아니야", "아니에요", "취소"
    );
    private static final List<String> EMERGENCY_CONFIRM_KEYWORDS = Arrays.asList(
            "안 괜찮아", "안괜찮아", "빨리", "지금", "빨리 신고", "신고해", "위급해", "위급", "심각해"
    );

    // 재생 제어 키워드
    private static final List<String> PAUSE_KEYWORDS = Arrays.asList("잠깐", "멈춰", "정지", "일시정지", "멈춰줘", "정지해줘");
    private static final List<String> RESUME_KEYWORDS = Arrays.asList("다시", "계속", "재생", "틀어", "틀어줘", "다시 틀어", "재개");
    private static final List<String> NEXT_KEYWORDS = Arrays.asList("다음", "건너뛰기", "스킵", "넘겨", "다음 곡", "다음으로");
    private static final List<String> STOP_KEYWORDS = Arrays.asList("그만", "종료", "끝", "꺼줘", "중지");

    // 모드 키워드 (단순화)
    private static final List<String> HOME_KEYWORDS = Arrays.asList(
            "홈으로", "홈 화면", "처음으로", "메인으로", "돌아가"
    );
    private static final List<String> LISTENING_KEYWORDS = Arrays.asList(
            "노래 들려줘", "음악 틀어줘", "노래 듣고 싶어", "음악 듣고 싶어", "감상 모드", "감상으로"
    );
    private static final List<String> EXERCISE_KEYWORDS = Arrays.asList(
            "체조 시작", "체조하고 싶어", "운동할래", "체조할래", "같이 운동해줘", "체조 모드", "운동 모드"
    );
    private static final List<String> EXERCISE_END_KEYWORDS = Arrays.asList(
            "체조 종료", "체조 끝", "운동 그만", "체조 그만"
    );

    // 연속 재생 키워드
    private static final List<String> CONTINUE_PLAYING_KEYWORDS = Arrays.asList(
            "계속 들려줘", "계속 틀어줘", "이어서", "계속"
    );
    private static final List<String> MORE_LIKE_THIS_KEYWORDS = Arrays.asList(
            "비슷한 노래", "이런 노래", "같은 느낌"
    );

    @Override
    public IntentResult classify(String text, Long userId) {
        // 키워드 기반 분석은 userId를 사용하지 않음
        String normalized = text.trim().toLowerCase();

        log.debug("의도 분석 시작: text='{}'", normalized);

        // 1. 응급 상황 (최우선)
        String matchedKeyword = findMatchedKeyword(normalized, EMERGENCY_KEYWORDS);
        if (matchedKeyword != null) {
            IntentResult result = IntentResult.builder()
                    .intent(Intent.EMERGENCY)
                    .confidence(1.0)
                    .build();
            result.addEntity("keyword", matchedKeyword);
            log.debug("응급 상황 감지: keyword='{}'", matchedKeyword);
            return result;
        }

        // 2. 응급 취소
        if (containsAny(normalized, EMERGENCY_CANCEL_KEYWORDS)) {
            log.debug("응급 상황 취소 감지");
            return IntentResult.of(Intent.EMERGENCY_CANCEL);
        }

        // 2-1. 응급 즉시 확정
        if (containsAny(normalized, EMERGENCY_CONFIRM_KEYWORDS)) {
            log.debug("응급 상황 즉시 확정 감지");
            return IntentResult.of(Intent.EMERGENCY_CONFIRM);
        }

        // 3. 재생 제어
        if (containsAny(normalized, PAUSE_KEYWORDS)) {
            return IntentResult.of(Intent.MUSIC_PAUSE);
        }
        if (containsAny(normalized, RESUME_KEYWORDS)) {
            return IntentResult.of(Intent.MUSIC_RESUME);
        }
        if (containsAny(normalized, NEXT_KEYWORDS)) {
            return IntentResult.of(Intent.MUSIC_NEXT);
        }
        if (containsAny(normalized, STOP_KEYWORDS)) {
            return IntentResult.of(Intent.MUSIC_STOP);
        }

        // 4. 연속 재생
        if (containsAny(normalized, CONTINUE_PLAYING_KEYWORDS)) {
            return IntentResult.of(Intent.PLAY_NEXT_IN_QUEUE);
        }
        if (containsAny(normalized, MORE_LIKE_THIS_KEYWORDS)) {
            return IntentResult.of(Intent.PLAY_MORE_LIKE_THIS);
        }

        // 5. 모드 관련 (단순화)
        if (containsAny(normalized, HOME_KEYWORDS)) {
            return IntentResult.of(Intent.MODE_HOME);
        }
        if (containsAny(normalized, LISTENING_KEYWORDS)) {
            return IntentResult.of(Intent.MODE_LISTENING);
        }
        if (containsAny(normalized, EXERCISE_KEYWORDS)) {
            return IntentResult.of(Intent.MODE_EXERCISE);
        }
        if (containsAny(normalized, EXERCISE_END_KEYWORDS)) {
            return IntentResult.of(Intent.MODE_EXERCISE_END);
        }

        // 6. 노래 검색 (가수 + 제목 패턴)
        IntentResult songIntent = detectSongSearchIntent(normalized);
        if (songIntent != null) {
            return songIntent;
        }

        // 7. 인식 불가
        return IntentResult.of(Intent.UNKNOWN);
    }

    /**
     * 노래 검색 의도 감지
     */
    private IntentResult detectSongSearchIntent(String text) {
        // "틀어줘", "들려줘", "재생" 등의 트리거 단어 제거
        String cleanText = text
                .replaceAll("(틀어줘|틀어|들려줘|들려|재생|노래|음악|해줘|해)", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleanText.isEmpty()) {
            return null;
        }

        // 패턴 1: "가수의 제목" (예: "태진아의 사랑은 아무나 하나")
        Pattern pattern1 = Pattern.compile("(.+)의\\s*(.+)");
        Matcher matcher1 = pattern1.matcher(cleanText);
        if (matcher1.find()) {
            String artist = matcher1.group(1).trim();
            String title = matcher1.group(2).trim();

            IntentResult result = IntentResult.builder()
                    .intent(Intent.SELECT_BY_ARTIST_TITLE)
                    .confidence(0.9)
                    .build();
            result.addEntity("artist", artist);
            result.addEntity("title", title);

            log.debug("노래 검색 감지 (가수+제목): artist='{}', title='{}'", artist, title);
            return result;
        }

        // 패턴 2: "가수 제목" (예: "태진아 사랑은 아무나 하나")
        String[] words = cleanText.split("\\s+");
        if (words.length >= 2) {
            String artist = words[0];
            String title = String.join(" ", Arrays.copyOfRange(words, 1, words.length));

            IntentResult result = IntentResult.builder()
                    .intent(Intent.SELECT_BY_ARTIST_TITLE)
                    .confidence(0.7)
                    .build();
            result.addEntity("artist", artist);
            result.addEntity("title", title);

            log.debug("노래 검색 감지 (추정 가수+제목): artist='{}', title='{}'", artist, title);
            return result;
        }

        // 패턴 3: 단일 단어 (가수명 또는 제목)
        IntentResult result = IntentResult.builder()
                .intent(Intent.SELECT_BY_ARTIST)
                .confidence(0.5)
                .build();
        result.addEntity("query", cleanText);

        log.debug("노래 검색 감지 (일반 검색): query='{}'", cleanText);
        return result;
    }

    /**
     * 키워드 포함 여부 확인
     */
    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    /**
     * 매칭된 키워드 찾기 (실제 매칭된 키워드 반환)
     */
    private String findMatchedKeyword(String text, List<String> keywords) {
        return keywords.stream()
                .filter(text::contains)
                .findFirst()
                .orElse(null);
    }

    @Override
    public String getClassifierType() {
        return "KEYWORD";
    }
}
