package com.familytracker.controller;

import com.familytracker.model.Book;
import com.familytracker.model.dto.OpenLibraryBookDto;
import com.familytracker.service.BookService;
import com.familytracker.service.OpenLibraryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/openlibrary")
@CrossOrigin
public class OpenLibraryController {

    private final OpenLibraryService olService;
    private final BookService bookService;

    public OpenLibraryController(OpenLibraryService olService, BookService bookService) {
        this.olService = olService;
        this.bookService = bookService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<OpenLibraryBookDto>> search(
        @RequestParam(required = false) String title
    ) {
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(olService.searchByTitle(title));
    }

    @GetMapping("/isbn/{isbn}")
    public ResponseEntity<OpenLibraryBookDto> searchByIsbn(@PathVariable String isbn) {
        return Optional.ofNullable(olService.searchByIsbn(isbn))
            .map((OpenLibraryBookDto dto) -> ResponseEntity.ok(dto)) // ✅ strongly-typed lambda
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/import")
    public ResponseEntity<Book> importBook(@Valid @RequestBody OpenLibraryBookDto dto) {
        Book book = new Book();
        book.setTitle(dto.getTitle());
        book.setAuthor(dto.getAuthor());
        book.setIsbn(dto.getIsbn());
        book.setCoverUrl(dto.getCoverUrl());

        Book saved = bookService.save(book);
        return ResponseEntity.ok(saved);
    }
}
