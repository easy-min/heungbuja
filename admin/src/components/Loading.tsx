import '../styles/loading.css';

interface LoadingProps {
  size?: 'small' | 'medium' | 'large';
  text?: string;
}

const Loading = ({ size = 'medium', text }: LoadingProps) => {
  return (
    <div className="loading-container">
      <div className={`spinner spinner-${size}`}></div>
      {text && <p className="loading-text">{text}</p>}
    </div>
  );
};

export default Loading;