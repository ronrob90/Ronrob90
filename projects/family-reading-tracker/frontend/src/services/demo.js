export const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === "true";

const catalog = [
  { title: "The Moonlit Library", author: "Sample Author", isbn: "" },
  { title: "A Garden of Stories", author: "Example Writer", isbn: "" },
  { title: "Little Explorers", author: "Demo Author", isbn: "" },
];
let books = [{ id: 1, ...catalog[0] }];

export async function demoAdapter(config) {
  let data;
  if (config.url === "/api/families") {
    data = [{ id: 1, familyName: "Example Family" }];
  } else if (config.url === "/api/books") {
    data = [...books];
  } else if (config.url.startsWith("/api/openlibrary/search?")) {
    const query = new URL(config.url, "https://demo.invalid").searchParams.get("title").toLowerCase();
    data = catalog.filter(book => book.title.toLowerCase().includes(query));
  } else if (config.url === "/api/openlibrary/import") {
    const book = typeof config.data === "string" ? JSON.parse(config.data) : config.data;
    data = { id: books.length + 1, ...book };
    books.push(data);
  } else {
    throw new Error("This action is unavailable in the sample demo.");
  }
  return { data, status: 200, statusText: "OK", headers: {}, config };
}
