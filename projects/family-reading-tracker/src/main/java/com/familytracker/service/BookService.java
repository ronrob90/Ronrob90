package com.familytracker.service;

import com.familytracker.model.Book;
import com.familytracker.repository.BookRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BookService {

    private final BookRepository repo;

    public BookService(BookRepository repo) {
        this.repo = repo;
    }

    public List<Book> getAllBooks() {
        return repo.findAll();
    }

    public Optional<Book> getById(Long id) {
        return repo.findById(id);
    }

    public Optional<Book> getByIsbn(String isbn) {
        return repo.findByIsbn(isbn);
    }

    public List<Book> searchByTitle(String title) {
        return repo.findByTitleContainingIgnoreCase(title);
    }

    public Book save(Book book) {
        return repo.save(book);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
