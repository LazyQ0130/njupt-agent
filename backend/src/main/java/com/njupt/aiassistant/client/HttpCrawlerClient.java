package com.njupt.aiassistant.client;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.dto.CustomCrawlerRequest;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.vo.CrawlerStatusVO;
import com.njupt.aiassistant.vo.CrawlerTriggerVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpCrawlerClient implements CrawlerClient {

    private final RestClient restClient;

    public HttpCrawlerClient(@Qualifier("crawlerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public CrawlerStatusVO status() {
        return get("/crawler/status", CrawlerStatusVO.class);
    }

    @Override
    public CrawlerTriggerVO run() {
        return post("/crawler/run", CrawlerTriggerVO.class);
    }

    @Override
    public CrawlerTriggerVO custom(CustomCrawlerRequest request) {
        return post("/crawler/custom", request, CrawlerTriggerVO.class);
    }

    @Override
    public CrawlerTriggerVO reindex() {
        return post("/crawler/reindex", CrawlerTriggerVO.class);
    }

    private <T> T get(String uri, Class<T> responseType) {
        try {
            var result = restClient.get().uri(uri).retrieve().body(responseType);
            return requireBody(result);
        } catch (RestClientResponseException exception) {
            throw failed(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private <T> T post(String uri, Class<T> responseType) {
        try {
            var result = restClient.post().uri(uri).retrieve().body(responseType);
            return requireBody(result);
        } catch (RestClientResponseException exception) {
            throw failed(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private <T> T post(String uri, Object body, Class<T> responseType) {
        try {
            var result = restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(responseType);
            return requireBody(result);
        } catch (RestClientResponseException exception) {
            throw failed(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private <T> T requireBody(T result) {
        if (result == null) {
            throw new BusinessException(
                    ErrorCode.CRAWLER_SERVICE_FAILED,
                    "知识采集服务返回空结果"
            );
        }
        return result;
    }

    private BusinessException failed(RestClientResponseException exception) {
        return new BusinessException(
                ErrorCode.CRAWLER_SERVICE_FAILED,
                "知识采集服务返回异常状态: " + exception.getStatusCode().value(),
                exception
        );
    }

    private BusinessException unavailable(RestClientException exception) {
        return new BusinessException(
                ErrorCode.CRAWLER_SERVICE_UNAVAILABLE,
                "知识采集服务暂时不可用，请稍后重试",
                exception
        );
    }
}
