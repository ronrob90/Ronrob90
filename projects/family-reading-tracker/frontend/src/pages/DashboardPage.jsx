import { useEffect, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import api from "../services/api";
import { DEMO_MODE } from "../services/demo";

function DashboardPage() {
  const [families, setFamilies] = useState([]);
  const [books, setBooks] = useState([]);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  useEffect(() => {
    const token = localStorage.getItem("token");
    if (!token && !DEMO_MODE) {
      navigate("/login");
      return;
    }
    let active = true;
    Promise.all([api.get("/api/families"), api.get("/api/books")])
      .then(([familyResponse, bookResponse]) => {
        if (!active) return;
        setFamilies(familyResponse.data);
        setBooks(bookResponse.data);
      })
      .catch(() => {
        if (active) setError("Unable to load the dashboard. Please log in again or retry.");
      });
    return () => {
      active = false;
    };
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem("token");
    navigate("/login");
  };

  return (
    <div>
      <h2>Dashboard</h2>
      {error && <p role="alert">{error}</p>}
      {!DEMO_MODE && <button onClick={handleLogout}>Logout</button>}
      {DEMO_MODE && <button onClick={() => window.location.reload()}>Reset sample data</button>}
      <br />
      <br />
      <Link className="button-link" to="/search">
        Search for books
      </Link>

      <h3>Families:</h3>
      <ul>
        {families.map((family) => (
          <li key={family.id}>{family.familyName}</li>
        ))}
      </ul>

      <h3>Imported Books:</h3>
      <ul>
        {books.map((book) => (
          <li key={book.id}>
            {book.title} {book.author && `by ${book.author}`}
          </li>
        ))}
      </ul>
    </div>
  );
}

export default DashboardPage;
