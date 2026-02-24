package fr.traqueur.victor.repository;

import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.dto.CourseDto;
import fr.traqueur.victor.entities.Course;

public interface CourseRepository extends Repository<CourseDto, Course, Long> {
}