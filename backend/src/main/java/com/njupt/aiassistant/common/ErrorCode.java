package com.njupt.aiassistant.common;

public enum ErrorCode {
    BAD_REQUEST(400, "请求参数不合法"),
    CRAWLER_UNAUTHORIZED(40101, "采集服务认证失败"),
    ANONYMOUS_SESSION_REQUIRED(40102, "匿名会话无效，请刷新页面后重试"),
    ADMIN_CREDENTIALS_INVALID(40103, "管理员账号或密码错误"),
    ADMIN_FORBIDDEN(40301, "无权访问管理员资源"),
    RATE_LIMIT_EXCEEDED(42901, "请求过于频繁，请稍后重试"),
    FILE_EMPTY(40001, "上传文件不能为空"),
    FILE_TYPE_NOT_ALLOWED(40002, "仅支持 PDF、DOC 和 DOCX 文件"),
    FILE_TOO_LARGE(40003, "上传文件超过大小限制"),
    DOCUMENT_NOT_FOUND(40401, "文档不存在"),
    DOCUMENT_BUSY(40901, "文档正在处理，暂时不能删除"),
    CONVERSATION_NOT_FOUND(40402, "会话不存在"),
    CHAT_HISTORY_NOT_FOUND(40403, "回答记录不存在"),
    RAG_SERVICE_FAILED(50201, "知识库检索服务调用失败"),
    AI_SERVICE_FAILED(50202, "AI 服务调用失败"),
    CRAWLER_SERVICE_FAILED(50203, "知识采集服务调用失败"),
    RAG_SERVICE_UNAVAILABLE(50301, "知识库检索服务不可用"),
    AI_SERVICE_UNAVAILABLE(50302, "AI 服务暂时不可用，请稍后重试。"),
    CRAWLER_SERVICE_UNAVAILABLE(50303, "知识采集服务暂时不可用"),
    ADMIN_CONFIGURATION_INVALID(50304, "管理员认证尚未配置"),
    AI_CONFIG_STORAGE_UNAVAILABLE(50305, "服务器尚未配置 AI Key 加密主密钥"),
    AI_CREDENTIALS_INVALID(40004, "DeepSeek API Key 无效或无权访问账户"),
    AI_BALANCE_SYNC_FAILED(50204, "暂时无法同步 DeepSeek 账户余额"),
    FILE_STORAGE_FAILED(50001, "文件保存失败"),
    DATA_SERIALIZATION_FAILED(50002, "数据序列化失败"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
