import type { Action, ActionIconMap } from '../../types/visualization';

interface ActionIndicatorProps {
  action: Action | null;
  isActive: boolean;
}

const ActionIndicator = ({ action, isActive }: ActionIndicatorProps) => {
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

  const icon = action ? actionIcons[action.actionCode] || '💃' : '🙌';
  const name = action?.actionName || '동작';

  return (
    <div className={`viz-action-indicator ${isActive ? 'active' : ''}`}>
      <div className="viz-action-icon">{icon}</div>
      <div className="viz-action-name">{name}</div>
    </div>
  );
};

export default ActionIndicator;
