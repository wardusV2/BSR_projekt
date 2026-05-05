package com.webproject.safelogin.repository;

import com.webproject.safelogin.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Integer> {
    List<Comment> findByVideoId(Integer videoId);
}
