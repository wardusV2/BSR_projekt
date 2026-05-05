package com.webproject.safelogin.controller;

import com.webproject.safelogin.model.Comment;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.model.Video;
import com.webproject.safelogin.repository.UserRepository;
import com.webproject.safelogin.repository.VideoRepository;
import com.webproject.safelogin.service.CommentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class CommentController {
    private final CommentService commentService;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;

    public CommentController(CommentService commentService, UserRepository userRepository, VideoRepository videoRepository) {
        this.commentService = commentService;
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
    }

    @PostMapping("/addComment")
    public ResponseEntity<?> addComment(@RequestBody Map<String, Object> request) {
        Integer userId = (Integer) request.get("userId");
        Integer videoId = ((Number) request.get("videoId")).intValue();
        String content = (String) request.get("content");

        User user = userRepository.findById(userId).orElseThrow();
        Video video = videoRepository.findById(videoId).orElseThrow();
        Comment comment = commentService.addComment(user, video, content);

        return ResponseEntity.ok(comment);
    }

    @GetMapping("/getVideoComments/{videoId}")
    public ResponseEntity<List<Map<String, Object>>> getComments(@PathVariable Integer videoId) {
        List<Comment> comments = commentService.getCommentsForVideo(videoId);

        List<Map<String, Object>> response = comments.stream().map(comment -> {
            Map<String, Object> data = new HashMap<>();
            data.put("userId", comment.getUser().getId());
            data.put("videoId", comment.getVideo().getId());
            data.put("content", comment.getContent());
            data.put("userNick", comment.getUser().getNick());
            return data;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
