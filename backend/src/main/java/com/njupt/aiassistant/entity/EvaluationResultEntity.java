package com.njupt.aiassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "evaluation_result")
public class EvaluationResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false, length = 2000)
    private String question;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String answer;

    @Column(name = "source_match", nullable = false)
    private Boolean sourceMatch;

    @Column(name = "has_source", nullable = false)
    private Boolean hasSource;

    @Column(name = "keyword_match", nullable = false)
    private Boolean keywordMatch;

    @Column(name = "non_empty", nullable = false)
    private Boolean nonEmpty;

    @Column(name = "expected_source", nullable = false, length = 500)
    private String expectedSource;

    @Column(name = "human_accurate")
    private Boolean humanAccurate;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(nullable = false)
    private Integer score;

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
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Boolean getSourceMatch() { return sourceMatch; }
    public void setSourceMatch(Boolean sourceMatch) { this.sourceMatch = sourceMatch; }
    public Boolean getHasSource() { return hasSource; }
    public void setHasSource(Boolean hasSource) { this.hasSource = hasSource; }
    public Boolean getKeywordMatch() { return keywordMatch; }
    public void setKeywordMatch(Boolean keywordMatch) { this.keywordMatch = keywordMatch; }
    public Boolean getNonEmpty() { return nonEmpty; }
    public void setNonEmpty(Boolean nonEmpty) { this.nonEmpty = nonEmpty; }
    public String getExpectedSource() { return expectedSource; }
    public void setExpectedSource(String expectedSource) { this.expectedSource = expectedSource; }
    public Boolean getHumanAccurate() { return humanAccurate; }
    public void setHumanAccurate(Boolean humanAccurate) { this.humanAccurate = humanAccurate; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
