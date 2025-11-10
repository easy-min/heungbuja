import '../styles/section.css';

interface SectionTitleProps {
  children: React.ReactNode;
}

const SectionTitle = ({ children }: SectionTitleProps) => {
  return <div className="section-title">{children}</div>;
};

export default SectionTitle;