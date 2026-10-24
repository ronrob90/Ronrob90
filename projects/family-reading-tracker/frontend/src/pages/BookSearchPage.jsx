import { useState } from "react";
import { Link } from "react-router-dom";
import api from "../services/api";

export default function BookSearchPage() {
  const [title, setTitle] = useState("");
  const [results, setResults] = useState([]);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSearch(event) {
    event.preventDefault();
    setBusy(true);
    setMessage("");
    try {
      const response = await api.get(`/api/openlibrary/search?title=${encodeURIComponent(title.trim())}`);
      setResults(response.data);
      setMessage(response.data.length ? `${response.data.length} results found.` : "No books found. Try another title.");
    } catch {
      setMessage("Search failed. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function handleImport(book) {
    setBusy(true);
    try {
      await api.post("/api/openlibrary/import", book);
      setMessage(`Added ${book.title}. Return to the dashboard to see your books.`);
    } catch {
      setMessage("Import failed. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return <section>
    <Link to="/">← Dashboard</Link>
    <h2>Book search</h2>
    <form onSubmit={handleSearch}>
      <label htmlFor="book-title">Book title</label>
      <div className="search-row">
        <input id="book-title" type="search" placeholder="Try garden" value={title}
          onChange={event => setTitle(event.target.value)} required />
        <button type="submit" disabled={busy || !title.trim()}>{busy ? "Working…" : "Search"}</button>
      </div>
    </form>
    <p role="status" aria-live="polite">{message}</p>
    <ul className="book-results">{results.map(book => <li key={`${book.title}-${book.author}-${book.isbn}`}>
      <span>{book.title} {book.author && `by ${book.author}`}</span>
      <button disabled={busy} onClick={() => handleImport(book)} aria-label={`Import ${book.title}`}>Import</button>
    </li>)}</ul>
  </section>;
}
