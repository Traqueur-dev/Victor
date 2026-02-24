package fr.traqueur.victor.core;

import fr.traqueur.victor.*;
import fr.traqueur.victor.dto.AuthorDto;
import fr.traqueur.victor.dto.BookDto;
import fr.traqueur.victor.dto.CourseDto;
import fr.traqueur.victor.dto.StudentDto;
import fr.traqueur.victor.dto.UserDto;
import fr.traqueur.victor.repository.AuthorRepository;
import fr.traqueur.victor.repository.BookRepository;
import fr.traqueur.victor.repository.CourseRepository;
import fr.traqueur.victor.repository.StudentRepository;
import fr.traqueur.victor.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractVictorTest {

    protected Victor victor;

    protected UserRepository userRepository;
    protected AuthorRepository authorRepo;
    protected BookRepository bookRepo;
    protected StudentRepository studentRepo;
    protected CourseRepository courseRepo;

    protected abstract VictorBuilder configureVictor();

    @BeforeEach
    void setUpBase() {
        VictorBuilder builder = configureVictor()
                .showSql()
                .autoMigrate()
                .dtos(
                        UserDto.class,
                        AuthorDto.class,
                        BookDto.class,
                        StudentDto.class,
                        CourseDto.class
                );

        victor = builder.build();

        userRepository = victor.createRepository(UserRepository.class);
        authorRepo = victor.createRepository(AuthorRepository.class);
        bookRepo = victor.createRepository(BookRepository.class);
        studentRepo = victor.createRepository(StudentRepository.class);
        courseRepo = victor.createRepository(CourseRepository.class);
    }

    @AfterEach
    void tearDownBase() {
        if (victor != null) {
            victor.close();
        }
    }
}