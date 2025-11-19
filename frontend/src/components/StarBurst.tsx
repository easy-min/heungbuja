import React from 'react';
import './StarBurst.css';

type StarConfig = {
  x: number;      // 최종 X 이동량(px)
  y: number;      // 최종 Y 이동량(px)
  size: number;
  delay: string;
};

const STARS: StarConfig[] = [
  { x: -400, y: -200, size: 84, delay: '0s' },    // 왼쪽 위
  { x:  100, y: -300, size: 102, delay: '0.1s' }, // 위
  { x:  450, y: -100, size: 120, delay: '0.2s' }, // 오른쪽 위
  { x: -475, y:   50, size: 108, delay: '0.3s' }, // 왼쪽
  { x:  500, y:  125, size: 96,  delay: '0.4s' }, // 오른쪽
  { x: -300, y:  350, size: 90,  delay: '0.5s' }, // 왼쪽 아래
  { x:  375, y:  350, size: 114, delay: '0.6s' }, // 오른쪽 아래
];

const StarBurst: React.FC = () => {
  return (
    <div className="starburst-root">
      <div className="starburst-rays" />
      {STARS.map((star, idx) => (
        <span
          key={idx}
          className="starburst-star"
          style={{
            width: star.size,
            height: star.size,
            animationDelay: star.delay,
            // CSS 커스텀 프로퍼티로 최종 이동량 전달
            '--x': `${star.x}px`,
            '--y': `${star.y}px`,
          } as React.CSSProperties}
        />
      ))}
    </div>
  );
};

export default StarBurst;
