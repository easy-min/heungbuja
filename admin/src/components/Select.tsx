import type { SelectHTMLAttributes } from 'react';
import '../styles/input.css';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  options: { value: string | number; label: string }[];
}

const Select = ({ label, error, options, className = '', ...props }: SelectProps) => {
  return (
    <div className="input-wrapper">
      {label && <label className="input-label">{label}</label>}
      <select
        className={`input ${error ? 'input-error' : ''} ${className}`}
        {...props}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error && <span className="input-error-message">{error}</span>}
    </div>
  );
};

export default Select;