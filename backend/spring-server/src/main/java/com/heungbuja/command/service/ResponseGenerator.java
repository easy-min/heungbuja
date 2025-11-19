package com.heungbuja.command.service;

import com.heungbuja.voice.enums.Intent;
import org.springframework.stereotype.Component;

/**
 * 응답 메시지 생성기
 * Intent에 따라 적절한 TTS 메시지 생성
 */
@Component
public class ResponseGenerator {

    /**
     * Intent에 따른 응답 메시지 생성
     */
    public String generateResponse(Intent intent, Object... args) {
        return switch (intent) {
            // 음악 재생
            case SELECT_BY_ARTIST, SELECT_BY_TITLE, SELECT_BY_ARTIST_TITLE -> {
                if (args.length >= 2) {
                    String artist = (String) args[0];
                    String title = (String) args[1];
                    yield String.format("%s의 '%s'를 재생할게요", artist, title);
                }
                yield "노래를 재생할게요";
            }

            // 재생 제어
            case MUSIC_PAUSE -> "일시정지할게요";
            case MUSIC_RESUME -> "계속 재생할게요";
            case MUSIC_NEXT -> "다음 곡을 재생할게요";
            case MUSIC_STOP -> "종료할게요";

            // 연속 재생
            case PLAY_NEXT_IN_QUEUE -> "다음 곡을 재생할게요";
            case PLAY_MORE_LIKE_THIS -> "비슷한 노래를 계속 들려드릴게요";

            // 모드 관련 (단순화)
            case MODE_HOME -> "홈 화면으로 돌아갈게요";
            case MODE_LISTENING -> "어떤 노래를 들려드릴까요?";
            case MODE_EXERCISE -> "체조를 시작할게요. 함께 운동해봐요!";
            case MODE_EXERCISE_NO_SONG -> "게임 목록을 보여드릴게요";
            case MODE_EXERCISE_END -> "수고하셨어요! 체조를 종료할게요";

            // 응급 상황
            case EMERGENCY -> "괜찮으세요? 대답해주세요!";
            case EMERGENCY_CANCEL -> "괜찮으시군요. 신고를 취소했습니다";
            case EMERGENCY_CONFIRM -> "알겠습니다. 지금 바로 신고하겠습니다";

            // 인식 불가
            case UNKNOWN -> "죄송합니다. 다시 한번 말씀해주세요";

            default -> "알 수 없는 명령입니다";
        };
    }

    /**
     * 노래 검색 실패 메시지
     */
    public String songNotFoundMessage(String query) {
        return String.format("'%s' 노래를 찾을 수 없어요. 다시 말씀해주세요", query);
    }

    /**
     * 다중 검색 결과 메시지
     */
    public String multipleResultsMessage(String title) {
        return String.format("'%s'를 부른 가수가 여러 명이에요. 누구의 노래를 들려드릴까요?", title);
    }

    /**
     * 에러 메시지
     */
    public String errorMessage() {
        return "죄송합니다. 처리 중 문제가 발생했어요";
    }
}
