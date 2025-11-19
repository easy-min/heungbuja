import { useRef, type ChangeEvent } from 'react';
import { useSimpleSongUploadStore } from '../stores/simpleSongUploadStore';
import styles from '../styles/SimpleSongUpload.module.css';

interface SimpleSongUploadModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const SimpleSongUploadModal = ({ isOpen, onClose }: SimpleSongUploadModalProps) => {
  const audioInputRef = useRef<HTMLInputElement>(null);
  const lyricsInputRef = useRef<HTMLInputElement>(null);

  const {
    step,
    audioFile,
    lyricsFile,
    title,
    artist,
    s3Key,
    isAnalyzing,
    progress,
    progressText,
    analyzedData,
    isRegistering,
    registeredSongId,
    error,
    setAudioFile,
    setLyricsFile,
    setTitle,
    setArtist,
    setS3Key,
    nextStep,
    prevStep,
    resetState,
    startAnalysis,
    confirmRegister,
  } = useSimpleSongUploadStore();

  const handleClose = () => {
    resetState();
    onClose();
  };

  const handleAudioFileSelect = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setAudioFile(file);
    }
  };

  const handleLyricsFileSelect = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setLyricsFile(file);
    }
  };

  const handleRemoveAudioFile = () => {
    setAudioFile(null);
    if (audioInputRef.current) {
      audioInputRef.current.value = '';
    }
  };

  const handleRemoveLyricsFile = () => {
    setLyricsFile(null);
    if (lyricsInputRef.current) {
      lyricsInputRef.current.value = '';
    }
  };

  const handleNextStep = () => {
    if (step === 1) {
      // 파일 검증
      if (!audioFile || !lyricsFile) {
        alert('오디오 파일과 가사 파일을 모두 업로드해주세요.');
        return;
      }
      nextStep();
    } else if (step === 2) {
      // 메타데이터 검증
      if (!title.trim() || !artist.trim() || !s3Key.trim()) {
        alert('곡 제목, 아티스트, S3 Key를 모두 입력해주세요.');
        return;
      }
      // 분석 시작
      startAnalysis();
    }
  };

  const handleConfirmRegister = () => {
    confirmRegister();
  };

  const formatTime = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = (seconds % 60).toFixed(2);
    return `${mins}:${secs.padStart(5, '0')}`;
  };

  const renderStepContent = () => {
    switch (step) {
      case 1:
        return (
          <div className={styles.stepContent}>
            {/* 오디오 파일 업로드 */}
            <div className={styles.inputGroup}>
              <label className={styles.label}>오디오 파일 (MP3, WAV 등)</label>
              <input
                ref={audioInputRef}
                type="file"
                accept="audio/*"
                onChange={handleAudioFileSelect}
                className={styles.hiddenInput}
              />
              <div
                className={`${styles.fileInputWrapper} ${audioFile ? styles.hasFile : ''}`}
                onClick={() => !audioFile && audioInputRef.current?.click()}
              >
                {audioFile ? (
                  <>
                    <span className={styles.fileIcon}>🎵</span>
                    <span className={styles.fileName}>{audioFile.name}</span>
                    <button
                      type="button"
                      className={styles.removeFileButton}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRemoveAudioFile();
                      }}
                    >
                      ×
                    </button>
                  </>
                ) : (
                  <>
                    <span className={styles.uploadIcon}>📁</span>
                    <span className={styles.placeholder}>클릭하여 오디오 파일 선택</span>
                  </>
                )}
              </div>
            </div>

            {/* 가사 파일 업로드 */}
            <div className={styles.inputGroup}>
              <label className={styles.label}>가사 파일 (TXT)</label>
              <input
                ref={lyricsInputRef}
                type="file"
                accept=".txt"
                onChange={handleLyricsFileSelect}
                className={styles.hiddenInput}
              />
              <div
                className={`${styles.fileInputWrapper} ${lyricsFile ? styles.hasFile : ''}`}
                onClick={() => !lyricsFile && lyricsInputRef.current?.click()}
              >
                {lyricsFile ? (
                  <>
                    <span className={styles.fileIcon}>📝</span>
                    <span className={styles.fileName}>{lyricsFile.name}</span>
                    <button
                      type="button"
                      className={styles.removeFileButton}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRemoveLyricsFile();
                      }}
                    >
                      ×
                    </button>
                  </>
                ) : (
                  <>
                    <span className={styles.uploadIcon}>📁</span>
                    <span className={styles.placeholder}>클릭하여 가사 파일 선택</span>
                  </>
                )}
              </div>
              <p className={styles.hint}>
                가사 형식: 각 줄마다 가사를 입력하세요. 타이밍은 자동으로 분석됩니다.
              </p>
            </div>
          </div>
        );

      case 2:
        return (
          <div className={styles.stepContent}>
            <div className={styles.inputGroup}>
              <label className={styles.label}>곡 제목</label>
              <input
                type="text"
                className={styles.input}
                placeholder="예: 아리랑"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </div>

            <div className={styles.inputGroup}>
              <label className={styles.label}>아티스트</label>
              <input
                type="text"
                className={styles.input}
                placeholder="예: 민요"
                value={artist}
                onChange={(e) => setArtist(e.target.value)}
              />
            </div>

            <div className={styles.inputGroup}>
              <label className={styles.label}>S3 Key</label>
              <input
                type="text"
                className={styles.input}
                placeholder="예: songs/arirang.mp3"
                value={s3Key}
                onChange={(e) => setS3Key(e.target.value)}
              />
              <p className={styles.hint}>
                S3에 업로드된 오디오 파일의 경로를 입력하세요.
              </p>
            </div>
          </div>
        );

      case 3:
        if (isAnalyzing) {
          return (
            <div className={styles.stepContent}>
              <div className={styles.progressContainer}>
                <div className={styles.progressBar}>
                  <div className={styles.progressFill} style={{ width: `${progress}%` }}>
                    {progress}%
                  </div>
                </div>
                <p className={styles.progressText}>{progressText}</p>
              </div>
            </div>
          );
        }

        if (analyzedData) {
          const bpm = analyzedData.beats.tempoMap[0]?.bpm.toFixed(1) || '0';
          const beatsCount = analyzedData.beats.beats.length;
          const lyricsCount = analyzedData.lyrics.lines.length;

          return (
            <div className={styles.stepContent}>
              {/* 요약 카드 */}
              <div className={styles.summaryCards}>
                <div className={styles.summaryCard}>
                  <div className={styles.summaryIcon}>⏱️</div>
                  <div className={styles.summaryValue}>{formatTime(analyzedData.duration)}</div>
                  <div className={styles.summaryLabel}>곡 길이</div>
                </div>
                <div className={styles.summaryCard}>
                  <div className={styles.summaryIcon}>💓</div>
                  <div className={styles.summaryValue}>{bpm}</div>
                  <div className={styles.summaryLabel}>BPM</div>
                </div>
                <div className={styles.summaryCard}>
                  <div className={styles.summaryIcon}>📝</div>
                  <div className={styles.summaryValue}>{lyricsCount}</div>
                  <div className={styles.summaryLabel}>가사 줄</div>
                </div>
              </div>

              {/* 비트 타임라인 미리보기 */}
              <div className={styles.previewSection}>
                <div className={styles.previewTitle}>
                  🎵 비트 타임라인 <span className={styles.badge}>{beatsCount}개 검출</span>
                </div>
                <div className={styles.beatTimeline}>
                  {analyzedData.beats.beats.slice(0, 20).map((beat, idx) => (
                    <div key={idx} className={styles.beatItem}>
                      [{idx + 1}] {formatTime(beat.t)}s
                    </div>
                  ))}
                  {beatsCount > 20 && (
                    <div className={styles.moreItems}>... 외 {beatsCount - 20}개</div>
                  )}
                </div>
              </div>

              {/* 가사 미리보기 */}
              <div className={styles.previewSection}>
                <div className={styles.previewTitle}>
                  📝 가사 타이밍 미리보기
                </div>
                <div className={styles.lyricsList}>
                  {analyzedData.lyrics.lines.slice(0, 10).map((line, idx) => (
                    <div key={idx} className={styles.lyricsItem}>
                      <div className={styles.lyricsText}>{line.text}</div>
                      <div className={styles.lyricsTiming}>
                        {formatTime(line.start)} → {formatTime(line.end)}
                      </div>
                    </div>
                  ))}
                  {lyricsCount > 10 && (
                    <div className={styles.moreItems}>... 외 {lyricsCount - 10}줄</div>
                  )}
                </div>
              </div>
            </div>
          );
        }

        return null;

      case 4:
        return (
          <div className={styles.stepContent}>
            <div className={styles.successContainer}>
              <div className={styles.successIcon}>✅</div>
              <h2 className={styles.successTitle}>곡 등록 완료!</h2>
              <div className={styles.successInfo}>
                <p><strong>곡 제목:</strong> {title}</p>
                <p><strong>아티스트:</strong> {artist}</p>
                <p><strong>곡 ID:</strong> {registeredSongId}</p>
                <p><strong>비트 수:</strong> {analyzedData?.beats.beats.length}개</p>
                <p><strong>가사 줄:</strong> {analyzedData?.lyrics.lines.length}줄</p>
              </div>
            </div>
          </div>
        );

      default:
        return null;
    }
  };

  if (!isOpen) return null;

  return (
    <div className={styles.modalOverlay}>
      <div className={styles.modalBox}>
        {/* 헤더 */}
        <div className={styles.modalHeader}>
          <button className={styles.closeButton} onClick={handleClose}>
            ×
          </button>
          <div className={styles.headerContent}>
            <div className={styles.iconWrapper}>
              <span className={styles.musicIcon}>🎵</span>
            </div>
            <h2 className={styles.modalTitle}>곡 간편 등록</h2>
            <p className={styles.modalSubtitle}>
              오디오와 가사만 업로드하면 자동으로 분석됩니다
            </p>
          </div>
        </div>

        {/* 바디 */}
        <div className={styles.modalBody}>
          {renderStepContent()}
        </div>

        {/* 푸터 */}
        <div className={styles.modalFooter}>
            {step === 1 && (
              <>
                <button className={`${styles.btn} ${styles.btnSecondary}`} onClick={handleClose}>
                  취소
                </button>
                <button
                  className={`${styles.btn} ${styles.btnPrimary}`}
                  onClick={handleNextStep}
                  disabled={!audioFile || !lyricsFile}
                >
                  다음
                </button>
              </>
            )}

            {step === 2 && (
              <>
                <button className={`${styles.btn} ${styles.btnSecondary}`} onClick={prevStep}>
                  이전
                </button>
                <button
                  className={`${styles.btn} ${styles.btnSuccess}`}
                  onClick={handleNextStep}
                  disabled={!title.trim() || !artist.trim() || !s3Key.trim()}
                >
                  분석 시작
                </button>
              </>
            )}

            {step === 3 && !isAnalyzing && analyzedData && (
              <>
                <button className={`${styles.btn} ${styles.btnSecondary}`} onClick={prevStep}>
                  수정
                </button>
                <button
                  className={`${styles.btn} ${styles.btnSuccess}`}
                  onClick={handleConfirmRegister}
                  disabled={isRegistering}
                >
                  {isRegistering ? '등록 중...' : '등록 확정'}
                </button>
              </>
            )}

            {step === 4 && (
              <button className={`${styles.btn} ${styles.btnPrimary}`} onClick={handleClose}>
                완료
              </button>
            )}
          </div>

        {/* 에러 메시지 */}
        {error && step !== 4 && (
          <div style={{ padding: '0 24px 20px' }}>
            <div className="error-message">{error}</div>
          </div>
        )}
      </div>
    </div>
  );
};

export default SimpleSongUploadModal;
