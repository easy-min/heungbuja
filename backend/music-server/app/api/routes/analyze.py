"""
오디오 분석 API 엔드포인트
"""
from fastapi import APIRouter, File, UploadFile, Form, HTTPException
from fastapi.responses import JSONResponse
import os
import tempfile
from pathlib import Path
from typing import List

from app.services.audio_analyzer import analyze_audio


router = APIRouter(prefix="/api", tags=["analyze"])


@router.post("/analyze")
async def analyze_song(
    audio: UploadFile = File(..., description="오디오 파일 (.mp3, .wav)"),
    lyrics: UploadFile = File(..., description="가사 텍스트 파일 (.txt)"),
    title: str = Form(..., description="곡 제목")
):
    """
    오디오 파일과 가사를 분석하여 박자 JSON과 가사 JSON을 반환

    - **audio**: 오디오 파일 (.mp3, .wav)
    - **lyrics**: 가사 텍스트 파일 (.txt) - 한 줄에 한 라인씩
    - **title**: 곡 제목
    """
    try:
        # 임시 파일로 저장
        with tempfile.TemporaryDirectory() as tmp_dir:
            # 오디오 파일 저장
            audio_path = os.path.join(tmp_dir, f"{title}_{audio.filename}")
            with open(audio_path, "wb") as f:
                content = await audio.read()
                f.write(content)

            # 가사 파일 저장 및 읽기
            lyrics_content = await lyrics.read()
            lyrics_text = lyrics_content.decode('utf-8')
            lyrics_lines = [line.strip() for line in lyrics_text.split('\n') if line.strip()]

            if len(lyrics_lines) < 2:
                raise HTTPException(status_code=400, detail="가사가 너무 짧습니다. 2줄 이상 필요합니다.")

            # 분석 실행
            beats_json, lyrics_json, duration = analyze_audio(
                audio_path=audio_path,
                lyrics_lines=lyrics_lines,
                song_id=1,  # 임시 ID, Spring에서 실제 ID 할당
                song_title=title
            )

            return {
                "success": True,
                "beats": beats_json,
                "lyrics": lyrics_json,
                "duration": duration
            }

    except HTTPException:
        raise
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"분석 중 오류 발생: {str(e)}")
