package com.familytracker.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime sessionDate;

    private int durationMinutes;

    private String notes;

    @ManyToOne
    private User user;

    @ManyToOne
    private Book book;
}
