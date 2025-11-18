import type { PeriodType } from '../../types/device';

interface PeriodTabsProps {
  selectedPeriod: PeriodType;
  onPeriodChange: (period: PeriodType) => void;
}

const PeriodTabs = ({ selectedPeriod, onPeriodChange }: PeriodTabsProps) => {
  const periods: { value: PeriodType; label: string }[] = [
    { value: 1, label: '오늘' },
    { value: 7, label: '일주일' },
    { value: 30, label: '한달' },
  ];

  return (
    <div className="period-tabs">
      {periods.map((period) => (
        <button
          key={period.value}
          className={`period-tab ${selectedPeriod === period.value ? 'active' : ''}`}
          onClick={() => onPeriodChange(period.value)}
        >
          {period.label}
        </button>
      ))}
    </div>
  );
};

export default PeriodTabs;
