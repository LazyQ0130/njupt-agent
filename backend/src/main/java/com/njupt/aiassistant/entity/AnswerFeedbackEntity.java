package com.njupt.aiassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "answer_feedback")
public class AnswerFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_history_id", nullable = false, unique = true)
    private Long chatHistoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_feedback", nullable = false, length = 16)
    private FeedbackType userFeedback;

    @Column(length = 1000)
    private String reason;

    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @PrePersist
    void prePersist() {
        if (createdTime == null) {
            createdTime = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChatHistoryId() { return chatHistoryId; }
    public void setChatHistoryId(Long chatHistoryId) { this.chatHistoryId = chatHistoryId; }
    public FeedbackType getUserFeedback() { return userFeedback; }
    public void setUserFeedback(FeedbackType userFeedback) { this.userFeedback = userFeedback; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
