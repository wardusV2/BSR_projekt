package com.webproject.safelogin.service;

import com.webproject.safelogin.model.Comment;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.model.Video;
import com.webproject.safelogin.repository.CommentRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentService {
    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    public Comment addComment(User user, Video video, String content) {
        Comment comment = new Comment();
        comment.setUser(user);
        comment.setVideo(video);
        comment.setContent(content);
        return commentRepository.save(comment);
    }

    public List<Comment> getCommentsForVideo(Integer videoId) {
        return commentRepository.findByVideoId(videoId);
    }
}
