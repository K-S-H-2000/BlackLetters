package com.example.BlackLetters_spring_boot.persistence;

import com.example.BlackLetters_spring_boot.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // 활성화된 카테고리만 조회 (삭제된 카테고리 제외)
    @Query("SELECT c FROM Category c WHERE (c.user IS NULL OR c.user.userId = :userId) AND c.isActive = true")
    List<Category> findGlobalAndUserCategories(@Param("userId") Long userId);
}
