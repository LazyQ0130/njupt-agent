package com.njupt.aiassistant.client;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.dto.RagSearchRequest;
import com.njupt.aiassistant.dto.WebDocumentIndexRequest;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.vo.RagIndexResponseVO;
import com.njupt.aiassistant.vo.RagSearchResultVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class HttpRagClient implements RagClient {

    private static final ParameterizedTypeReference<List<RagSearchResultVO>> RESULT_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient searchRestClient;
    private final RestClient indexRestClient;

    public HttpRagClient(
            @Qualifier("ragRestClient") RestClient searchRestClient,
            @Qualifier("ragIndexRestClient") RestClient indexRestClient
    ) {
        this.searchRestClient = searchRestClient;
        this.indexRestClient = indexRestClient;
    }

    @Override
    public List<RagSearchResultVO> search(RagSearchRequest request) {
        try {
            var results = searchRestClient.post()
                    .uri("/rag/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RESULT_TYPE);
            return results == null ? List.of() : results;
        } catch (RestClientResponseException exception) {
            throw new BusinessException(
                    ErrorCode.RAG_SERVICE_FAILED,
                    "知识库检索服务返回异常状态: " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.RAG_SERVICE_UNAVAILABLE,
                    "暂时无法连接知识库检索服务",
                    exception
            );
        }
    }

    @Override
    public RagIndexResponseVO index(
            Path path,
            String filename,
            String source,
            LocalDateTime uploadTime
    ) {
        var multipart = new MultipartBodyBuilder();
        multipart.part("file", new NamedFileSystemResource(path, filename));
        multipart.part("source", source);
        multipart.part("upload_time", uploadTime.toString());
        try {
            var result = indexRestClient.post()
                    .uri("/documents/index")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart.build())
                    .retrieve()
                    .body(RagIndexResponseVO.class);
            if (result == null) {
                throw new BusinessException(
                        ErrorCode.RAG_SERVICE_FAILED,
                        "知识库索引服务返回空结果"
                );
            }
            return result;
        } catch (RestClientResponseException exception) {
            throw new BusinessException(
                    ErrorCode.RAG_SERVICE_FAILED,
                    "知识库索引服务返回异常状态: " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.RAG_SERVICE_UNAVAILABLE,
                    "暂时无法连接知识库索引服务",
                    exception
            );
        }
    }

    @Override
    public RagIndexResponseVO indexWeb(WebDocumentIndexRequest request) {
        try {
            var result = indexRestClient.post()
                    .uri("/documents/index-text")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RagIndexResponseVO.class);
            if (result == null) {
                throw new BusinessException(
                        ErrorCode.RAG_SERVICE_FAILED,
                        "网页知识索引服务返回空结果"
                );
            }
            return result;
        } catch (RestClientResponseException exception) {
            throw new BusinessException(
                    ErrorCode.RAG_SERVICE_FAILED,
                    "网页知识索引服务返回异常状态: " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.RAG_SERVICE_UNAVAILABLE,
                    "暂时无法连接网页知识索引服务",
                    exception
            );
        }
    }

    @Override
    public void delete(String documentId) {
        try {
            indexRestClient.delete()
                    .uri("/documents/{documentId}", documentId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new BusinessException(
                    ErrorCode.RAG_SERVICE_FAILED,
                    "知识库删除服务返回异常状态: "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.RAG_SERVICE_UNAVAILABLE,
                    "暂时无法连接知识库删除服务",
                    exception
            );
        }
    }

    private static final class NamedFileSystemResource extends FileSystemResource {

        private final String filename;

        private NamedFileSystemResource(Path path, String filename) {
            super(path);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
