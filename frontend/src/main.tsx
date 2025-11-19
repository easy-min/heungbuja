import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  // api 2번 요청되므로 StrictMode 주석 처리함
  // <StrictMode> 
    <App />
  // </StrictMode>,
)
