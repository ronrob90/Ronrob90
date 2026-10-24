package com.familytracker.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenLibraryBookDto {

    @NotBlank
    private String title;

    private String author;
    private String isbn;
    private String coverUrl;
}
