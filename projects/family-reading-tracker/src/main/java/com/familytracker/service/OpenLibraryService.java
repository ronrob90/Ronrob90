package com.familytracker.service;

import com.familytracker.model.dto.OpenLibraryBookDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OpenLibraryService {

    private final RestTemplate restTemplate = new RestTemplate();

    public List<OpenLibraryBookDto> searchByTitle(String title) {
        String url = "https://openlibrary.org/search.json?title=" + title.replace(" ", "+");

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        List<OpenLibraryBookDto> results = new ArrayList<>();
        if (response != null && response.containsKey("docs")) {
            List<Map<String, Object>> docs = (List<Map<String, Object>>) response.get("docs");

            for (Map<String, Object> doc : docs) {
                OpenLibraryBookDto dto = new OpenLibraryBookDto();
                dto.setTitle((String) doc.get("title"));

                List<String> authors = (List<String>) doc.get("author_name");
                if (authors != null && !authors.isEmpty()) {
                    dto.setAuthor(authors.get(0));
                }

                List<String> isbns = (List<String>) doc.get("isbn");
                if (isbns != null && !isbns.isEmpty()) {
                    dto.setIsbn(isbns.get(0));
                }

                if (doc.containsKey("cover_i")) {
                    int coverId = (int) doc.get("cover_i");
                    dto.setCoverUrl("https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg");
                }

                results.add(dto);
            }
        }

        return results;
    }

    public OpenLibraryBookDto searchByIsbn(String isbn) {
        String url =
            "https://openlibrary.org/api/books?bibkeys=ISBN:" + isbn + "&format=json&jscmd=data";

        Map<String, Object> result = restTemplate.getForObject(url, Map.class);
        if (result == null || result.isEmpty()) return null;

        Map<String, Object> bookData = (Map<String, Object>) result.get("ISBN:" + isbn);
        if (bookData == null) return null;

        OpenLibraryBookDto dto = new OpenLibraryBookDto();
        dto.setTitle((String) bookData.get("title"));
        dto.setIsbn(isbn);

        List<Map<String, String>> authors = (List<Map<String, String>>) bookData.get("authors");
        if (authors != null && !authors.isEmpty()) {
            dto.setAuthor(authors.get(0).get("name"));
        }

        if (bookData.containsKey("cover")) {
            Map<String, String> cover = (Map<String, String>) bookData.get("cover");
            dto.setCoverUrl(cover.get("large"));
        }

        return dto;
    }
}
