import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useVisualizationStore } from '../stores';
import { getSongs, getSongVisualization } from '../api/visualization';
import { useVisualizationPlayer } from '../hooks';
import { Button } from '../components';
import SongSelector from '../components/visualization/SongSelector';
import VisualizationHeader from '../components/visualization/VisualizationHeader';
import PlaybackControls from '../components/visualization/PlaybackControls';
import SectionDisplay from '../components/visualization/SectionDisplay';
import ProgressBar from '../components/visualization/ProgressBar';
import ActionIndicator from '../components/visualization/ActionIndicator';
import Timeline from '../components/visualization/Timeline';
import '../styles/visualization.css';

const SongVisualizationPage = () => {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(true);

  const {
    songs,
    setSongs,
    selectedSongId,
    setSelectedSongId,
    visualizationData,
    setVisualizationData,
    setError,
    reset,
    currentTime,
    currentSection,
    currentAction,
    selectedLevel,
    setSelectedLevel,
  } = useVisualizationStore();

  // 재생 로직 훅
  const { play, pause, stop, isPlaying } = useVisualizationPlayer();

  // 초기 데이터 로드
  useEffect(() => {
    loadSongs();

    return () => {
      reset(); // 컴포넌트 언마운트 시 상태 초기화
    };
  }, []);

  // 곡 목록 로드
  const loadSongs = async () => {
    try {
      setIsLoading(true);
      const songList = await getSongs();
      setSongs(songList);

      // 첫 번째 곡 자동 선택
      if (songList.length > 0) {
        const firstSongId = songList[0].id;
        setSelectedSongId(firstSongId);
        await loadVisualization(firstSongId);
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '곡 목록을 불러오는데 실패했습니다.';
      setError(errorMessage);
      console.error('곡 목록 로드 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 시각화 데이터 로드
  const loadVisualization = async (songId: number) => {
    try {
      const data = await getSongVisualization(songId);
      setVisualizationData(data);
      console.log('시각화 데이터 로드 완료:', data);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '시각화 데이터를 불러오는데 실패했습니다.';
      setError(errorMessage);
      console.error('시각화 데이터 로드 실패:', error);
    }
  };

  // 대시보드로 돌아가기
  const handleBackToDashboard = () => {
    navigate('/dashboard');
  };

  if (isLoading) {
    return (
      <div className="viz-page">
        <div className="viz-loading">
          <p>곡 목록을 불러오는 중...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="viz-page">
      {/* 헤더 - 뒤로가기 & 곡 선택 */}
      <div className="viz-page-header">
        <Button variant="primary" onClick={handleBackToDashboard}>
          ← 대시보드로 돌아가기
        </Button>

        <SongSelector
          songs={songs}
          selectedSongId={selectedSongId}
          onSelect={async (songId) => {
            setSelectedSongId(songId);
            reset();
            await loadVisualization(songId);
          }}
        />
      </div>

      {/* 시각화 컨텐츠 영역 */}
      <div className="viz-content">
        {!visualizationData ? (
          <div className="viz-empty-state">
            <p>곡을 선택하면 시각화 데이터를 볼 수 있습니다.</p>
          </div>
        ) : (
          <div className="viz-container">
            {/* 헤더 */}
            <VisualizationHeader
              title={songs.find(s => s.id === selectedSongId)?.title || ''}
              artist={songs.find(s => s.id === selectedSongId)?.artist || ''}
              bpm={visualizationData.bpm}
              duration={visualizationData.duration}
            />

            {/* 재생 컨트롤 */}
            <div className="viz-controls-sticky">
            <PlaybackControls
              isPlaying={isPlaying}
              onPlay={play}
              onPause={pause}
              onStop={stop}
              selectedLevel={selectedLevel}
              onLevelChange={setSelectedLevel}
            />
            </div>

            {/* 타임라인 섹션 */}
            <div className="viz-section">
              {/* 현재 구간 */}
              <SectionDisplay
                sectionName={currentSection}
                currentTime={currentTime}
                totalTime={visualizationData.duration}
              />

              {/* 진행 바 */}
              <ProgressBar
                currentTime={currentTime}
                duration={visualizationData.duration}
                sections={visualizationData.songBeat.sections || []}
                beats={visualizationData.songBeat.beats || []}
              />

              {/* 가사 표시 */}
              {/* <KaraokeLyrics
                currentLyric={currentLyric}
                nextLyric={
                  currentLyric
                    ? visualizationData.lyricsInfo?.lines.find(
                        (line) => line.start > currentLyric.end
                      ) || null
                    : null
                }
                currentTime={currentTime}
              /> */}

              {/* 동작 표시 */}
              <ActionIndicator
                action={currentAction}
                isActive={currentAction !== null}
              />

              {/* 타임라인 */}
              <Timeline
                lyrics={visualizationData.lyricsInfo?.lines || []}
                actions={[
                  ...(visualizationData.verse1Timeline || []),
                  ...(visualizationData.verse2Timelines?.[selectedLevel] || []),
                ]}
                currentTime={currentTime}
                currentAction={currentAction}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default SongVisualizationPage;
