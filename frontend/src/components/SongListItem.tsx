// components/SongListItem.tsx
import type { Song } from '@/types/song';
import './SongListItem.css';

interface Props {
  song: Song;
}

function SongListItem({ song }: Props) {
  return (
    <div className="song-item">
      <div className="song-item__rank">{song.rank}</div>

      <div className="song-item__card">
        <span className="song-item__title">{song.title}</span>
        <span className="song-item__artist">{song.artist}</span>
      </div>
    </div>
  );
}

export default SongListItem;
