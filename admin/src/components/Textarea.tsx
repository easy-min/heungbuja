import type { TextareaHTMLAttributes } from 'react';
import '../styles/input.css';

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
}

const Textarea = ({ label, error, className = '', ...props }: TextareaProps) => {
  return (
    <div className="input-wrapper">
      {label && <label className="input-label">{label}</label>}
      <textarea
        className={`input textarea ${error ? 'input-error' : ''} ${className}`}
        {...props}
      />
      {error && <span className="input-error-message">{error}</span>}
    </div>
  );
};

export default Textarea;