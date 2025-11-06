import '../styles/websocket.css';

interface WebSocketStatusProps {
  isConnected: boolean;
  isConnecting: boolean;
}

const WebSocketStatus = ({ isConnected, isConnecting }: WebSocketStatusProps) => {
  const getStatusText = () => {
    if (isConnecting) return '연결 중...';
    if (isConnected) return '실시간 연결됨';
    return '연결 끊김';
  };

  const getStatusClass = () => {
    if (isConnecting) return 'connecting';
    if (isConnected) return 'connected';
    return 'disconnected';
  };

  return (
    <div className="ws-status">
      <div className={`ws-indicator ${getStatusClass()}`}></div>
      {getStatusText()}
    </div>
  );
};

export default WebSocketStatus;