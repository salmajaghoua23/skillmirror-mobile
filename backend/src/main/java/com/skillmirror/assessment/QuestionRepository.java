package com.skillmirror.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findBySkillIdAndLevel(UUID skillId, String level);

    /** Nombre de questions par compétence : [skillId, count]. */
    @Query("select q.skillId, count(q) from Question q group by q.skillId")
    List<Object[]> countBySkill();
}