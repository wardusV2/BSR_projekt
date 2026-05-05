package com.webproject.safelogin.TestService;

import com.webproject.safelogin.service.CommentService;
import com.webproject.safelogin.model.Comment;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.model.Video;
import com.webproject.safelogin.repository.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private CommentService commentService;

    private User user;
    private Video video;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        user = new User();
        user.setId(1);
        user.setNick("TestUser");

        video = new Video();
        video.setId(2);
        video.setTitle("Test Video");
    }

    @Test
    void addComment_ShouldSaveAndReturnComment() {
        Comment commentToSave = new Comment();
        commentToSave.setUser(user);
        commentToSave.setVideo(video);
        commentToSave.setContent("Test content");

        Comment savedComment = new Comment();
        savedComment.setId(10);
        savedComment.setUser(user);
        savedComment.setVideo(video);
        savedComment.setContent("Test content");

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);

        Comment result = commentService.addComment(user, video, "Test content");

        assertNotNull(result);
        assertEquals(10, result.getId());
        assertEquals("Test content", result.getContent());
        assertEquals(user, result.getUser());
        assertEquals(video, result.getVideo());

        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void getCommentsForVideo_ShouldReturnComments() {
        Comment c1 = new Comment();
        c1.setId(1);
        c1.setContent("Comment 1");

        Comment c2 = new Comment();
        c2.setId(2);
        c2.setContent("Comment 2");

        List<Comment> mockComments = List.of(c1, c2);

        when(commentRepository.findByVideoId(2)).thenReturn(mockComments);

        List<Comment> result = commentService.getCommentsForVideo(2);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Comment 1", result.get(0).getContent());
        assertEquals("Comment 2", result.get(1).getContent());

        verify(commentRepository).findByVideoId(2);
    }
}

