package fr.traqueur.victor.dto;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entities.Course;
import fr.traqueur.victor.entities.Dto;

@Table(table = "courses")
public record CourseDto(
    @Id Long id,
    @Column(nullable = false, length = 200) String title
) implements Dto<Course> {

    @Override
    public Course toModel() {
        Course course = new Course();
        course.setId(id);
        return course;
    }
}