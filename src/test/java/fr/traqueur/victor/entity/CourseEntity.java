package fr.traqueur.victor.entity;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.model.Course;
import fr.traqueur.victor.entity.Entity;

@Table(table = "courses")
public record CourseEntity(
    @Id Long id,
    @Column(nullable = false, length = 200) String title
) implements Entity<Course> {

    @Override
    public Course toModel() {
        Course course = new Course();
        course.setId(id);
        return course;
    }
}