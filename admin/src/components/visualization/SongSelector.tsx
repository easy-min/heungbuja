import type { Song } from '../../types/visualization';

interface SongSelectorProps {
  songs: Song[];
  selectedSongId: number | null;
  onSelect: (songId: number) => void;
  isLoading?: boolean;
}

const SongSelector = ({
  songs,
  selectedSongId,
  onSelect,
  isLoading = false
}: SongSelectorProps) => {
  const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const songId = parseInt(e.target.value);
    if (songId) {
      onSelect(songId);
    }
  };

  return (
    <div className="viz-song-selector-wrapper">
      <label htmlFor="song-select">곡 선택:</label>
      <select
        id="song-select"
        value={selectedSongId || ''}
        onChange={handleChange}
        className="viz-song-select"
        disabled={isLoading}
      >
        <option value="">곡을 선택하세요...</option>
        {songs.map((song) => (
          <option key={song.id} value={song.id}>
            {song.title} - {song.artist}
          </option>
        ))}
      </select>
    </div>
  );
};

export default SongSelector;
