import { HashRouter as Router, Routes, Route } from "react-router-dom";
import { DEMO_MODE } from "./services/demo";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import DashboardPage from "./pages/DashboardPage";
import BookSearchPage from "./pages/BookSearchPage";

function App() {
  return (
    <Router>
      <header className="app-header"><p className="eyebrow">A family reading project</p><h1>Book Worm Buddies</h1></header>
      {DEMO_MODE && <aside role="note" className="demo-notice"><strong>Sample-data demo</strong> — Fictional books and family. Search “library,” “garden,” or “explorers.” No login, external API calls, or saved personal data. <a href="/#reading-tracker">Back to portfolio</a></aside>}
      <main className="reading-panel">
      <Routes>
        {!DEMO_MODE && <Route path="/login" element={<LoginPage />} />}
        {!DEMO_MODE && <Route path="/register" element={<RegisterPage />} />}
        <Route path="/" element={<DashboardPage />} />
        <Route path="/search" element={<BookSearchPage />} />
      </Routes>
      </main>
    </Router>
  );
}

export default App;
