package com.njupt.aiassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
        @NotBlank(message = "管理员账号不能为空")
        @Size(max = 128, message = "管理员账号过长")
        String username,
        @NotBlank(message = "管理员密码不能为空")
        @Size(max = 256, message = "管理员密码过长")
        String password
) {
}
