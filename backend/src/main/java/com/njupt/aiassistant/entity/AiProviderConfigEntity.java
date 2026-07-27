package com.njupt.aiassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_provider_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_provider_config_provider",
                columnNames = "provider"
        )
)
public class AiProviderConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "encrypted_api_key", length = 2048)
    private String encryptedApiKey;

    @Column(name = "key_last_four", length = 8)
    private String keyLastFour;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "verified_time")
    private LocalDateTime verifiedTime;

    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @PrePersist
    void prePersist() {
        var now = LocalDateTime.now();
        if (createdTime == null) {
            createdTime = now;
        }
        updatedTime = now;
        if (enabled == null) {
            enabled = false;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getEncryptedApiKey() { return encryptedApiKey; }
    public void setEncryptedApiKey(String encryptedApiKey) { this.encryptedApiKey = encryptedApiKey; }
    public String getKeyLastFour() { return keyLastFour; }
    public void setKeyLastFour(String keyLastFour) { this.keyLastFour = keyLastFour; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getVerifiedTime() { return verifiedTime; }
    public void setVerifiedTime(LocalDateTime verifiedTime) { this.verifiedTime = verifiedTime; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
}
