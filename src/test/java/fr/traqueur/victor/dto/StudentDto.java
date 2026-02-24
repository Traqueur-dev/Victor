package fr.traqueur.victor.dto;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.ManyToMany;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Student;

import java.util.List;

@Table(table = "students")
public record StudentDto(
    @Id Long id,
    @Column(nullable = false, length = 100) String name,
    @ManyToMany(
        joinTable = "student_courses",
        joinColumn = "student_id",
        inverseJoinColumn = "course_id"
    ) List<CourseDto> courses
) implements Dto<Student> {

    @Override
    public Student toModel() {
        Student student = new Student();
        student.setId(id);
        return student;
    }
}