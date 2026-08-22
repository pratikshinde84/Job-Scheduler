import ReactDOM from 'react-dom/client'
import App from './App'
import './styles/globals.css'

// Note: React.StrictMode is intentionally omitted.
// StrictMode double-invokes useEffect in development, which breaks Supabase's
// OAuth token parsing — the subscription is torn down and recreated after the
// SIGNED_IN event has already fired, causing an infinite redirect to /login.
ReactDOM.createRoot(document.getElementById('root')!).render(<App />)
