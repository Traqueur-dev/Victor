package fr.traqueur.victor.repository;

import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.dto.StudentDto;
import fr.traqueur.victor.entities.Student;

public interface StudentRepository extends Repository<StudentDto, Student, Long> {
}