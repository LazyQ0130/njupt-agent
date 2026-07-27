package com.njupt.aiassistant.service.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.vo.AiBalanceVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DeepSeekAccountClient {
    private final RestClient restClient;

    public DeepSeekAccountClient(@Qualifier("deepSeekRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public AiBalanceVO fetchBalance(String apiKey) {
        try {
            var response = restClient.get()
                    .uri("/user/balance")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .body(BalanceResponse.class);
            if (response == null || response.balanceInfos() == null) {
                throw new BusinessException(
                        ErrorCode.AI_BALANCE_SYNC_FAILED,
                        "DeepSeek 余额接口返回了无效数据"
                );
            }
            return new AiBalanceVO(
                    response.available(),
                    "SUCCESS",
                    LocalDateTime.now(),
                    response.available() ? "账户可用" : "余额不足",
                    response.balanceInfos().stream()
                            .map(item -> new AiBalanceVO.BalanceItem(
                                    item.currency(),
                                    item.totalBalance(),
                                    item.grantedBalance(),
                                    item.toppedUpBalance()
                            ))
                            .toList()
            );
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401
                    || exception.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.AI_CREDENTIALS_INVALID);
            }
            throw new BusinessException(
                    ErrorCode.AI_BALANCE_SYNC_FAILED,
                    "DeepSeek 账户验证失败，请稍后重试",
                    exception
            );
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.AI_BALANCE_SYNC_FAILED,
                    "暂时无法连接 DeepSeek，请稍后重试",
                    exception
            );
        }
    }

    record BalanceResponse(
            @JsonProperty("is_available") boolean available,
            @JsonProperty("balance_infos") List<BalanceInfo> balanceInfos
    ) {
    }

    record BalanceInfo(
            String currency,
            @JsonProperty("total_balance") String totalBalance,
            @JsonProperty("granted_balance") String grantedBalance,
            @JsonProperty("topped_up_balance") String toppedUpBalance
    ) {
    }
}
