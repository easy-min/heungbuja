import type { Action, ActionIconMap } from '../../types/visualization';

interface ActionChipProps {
  action: Action;
  index: number;
  isActive: boolean;
}

const ActionChip = ({ action, index, isActive }: ActionChipProps) => {
  // 동작 이모지 매핑
  const actionIcons: ActionIconMap = {
    0: '👏',      // 손 박수
    1: '👐',      // 팔 치기
    2: '🍑',      // 엉덩이 박수
    3: '🙆‍♀️',  // 팔 뻗기
    4: '🤸',      // 기우뚱
    5: '🚪',      // 비상구
    6: '🙋',      // 겨드랑이박수
    7: '💃',      // 기타
  };

  const icon = actionIcons[action.actionCode] || '💃';

  return (
    <div className={`viz-action-chip ${isActive ? 'active' : ''}`}>
      <span className="viz-action-chip-icon">{icon}</span>
      <span className="viz-action-chip-name">{action.actionName}</span>
      <span className="viz-action-chip-index">#{index + 1}</span>
    </div>
  );
};

export default ActionChip;
