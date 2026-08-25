import React from 'react'
import ReactDOM from 'react-dom/client'
// HashRouter כדי שהניווט יעבוד גם בתוך אפליקציית אנדרואיד (Capacitor WebView)
import { HashRouter } from 'react-router-dom'
import App from './App'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <HashRouter>
      <App />
    </HashRouter>
  </React.StrictMode>
)
