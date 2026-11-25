package com.skillmirror.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findBySkillIdAndLevel(UUID skillId, String level);

    /** Nombre de questions par compétence et par niveau : [skillId, level, count]. */
    @Query("select q.skillId, q.level, count(q) from Question q group by q.skillId, q.level")
    List<Object[]> countBySkillAndLevel();
}