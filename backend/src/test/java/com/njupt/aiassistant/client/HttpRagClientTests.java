package com.njupt.aiassistant.client;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.dto.RagSearchRequest;
import com.njupt.aiassistant.dto.WebDocumentIndexRequest;
import com.njupt.aiassistant.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpRagClientTests {

    @TempDir
    Path tempDirectory;

    @Test
    void forwardsQuestionAndMapsSearchResults() {
        var builder = RestClient.builder().baseUrl("http://localhost:8090");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new HttpRagClient(
                builder.build(),
                RestClient.create("http://index-client.invalid")
        );
        server.expect(once(), requestTo("http://localhost:8090/rag/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"question":"南邮转专业条件","top_k":3}
                        """))
                .andRespond(withSuccess("""
                        [
                          {
                            "content":"学生申请转专业应关注当年度通知。",
                            "filename":"本科生转专业管理办法.pdf",
                            "page":3,
                            "source":"教务处",
                            "score":0.93
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var results = client.search(new RagSearchRequest("南邮转专业条件", 3));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).filename()).isEqualTo("本科生转专业管理办法.pdf");
        assertThat(results.get(0).page()).isEqualTo(3);
        assertThat(results.get(0).source()).isEqualTo("教务处");
        assertThat(results.get(0).score()).isEqualTo(0.93);
        server.verify();
    }

    @Test
    void mapsRagHttpFailureToBusinessError() {
        var builder = RestClient.builder().baseUrl("http://localhost:8090");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new HttpRagClient(
                builder.build(),
                RestClient.create("http://index-client.invalid")
        );
        server.expect(once(), requestTo("http://localhost:8090/rag/search"))
                .andRespond(withServerError());

        assertThatThrownBy(
                () -> client.search(new RagSearchRequest("测试问题", null))
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.RAG_SERVICE_FAILED)
                );
        server.verify();
    }

    @Test
    void uploadsStoredDocumentUsingOriginalFilename() throws Exception {
        var storedPath = tempDirectory.resolve("stored-file.pdf");
        Files.writeString(storedPath, "%PDF-1.7 test");
        var builder = RestClient.builder().baseUrl("http://localhost:8090");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new HttpRagClient(
                RestClient.create("http://search-client.invalid"),
                builder.build()
        );
        server.expect(once(), requestTo("http://localhost:8090/documents/index"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> assertThat(
                        request.getHeaders().getContentType()
                ).isNotNull())
                .andRespond(withSuccess("""
                        {
                          "document_id":"doc-1",
                          "filename":"教务管理规定.pdf",
                          "chunks_indexed":4
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.index(
                storedPath,
                "教务管理规定.pdf",
                "教务处",
                LocalDateTime.of(2026, 7, 26, 10, 0)
        );

        assertThat(response.documentId()).isEqualTo("doc-1");
        assertThat(response.filename()).isEqualTo("教务管理规定.pdf");
        assertThat(response.chunksIndexed()).isEqualTo(4);
        server.verify();
    }

    @Test
    void indexesWebDocumentsUsingIndexClient() {
        var builder = RestClient.builder().baseUrl("http://localhost:8090");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new HttpRagClient(
                RestClient.create("http://search-client.invalid"),
                builder.build()
        );
        server.expect(once(), requestTo("http://localhost:8090/documents/index-text"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "document_id":"web-doc-1",
                          "filename":"学校简介",
                          "chunks_indexed":2,
                          "category":"SCHOOL_OVERVIEW"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.indexWeb(new WebDocumentIndexRequest(
                "学校简介",
                "南京邮电大学学校简介正文，用于验证网页知识索引使用独立的长超时客户端。",
                "南京邮电大学",
                "https://www.njupt.edu.cn/about/list.htm",
                "SCHOOL_OVERVIEW",
                LocalDateTime.of(2026, 7, 27, 12, 0),
                LocalDateTime.of(2026, 7, 27, 11, 0),
                "a".repeat(64)
        ));

        assertThat(response.documentId()).isEqualTo("web-doc-1");
        assertThat(response.chunksIndexed()).isEqualTo(2);
        server.verify();
    }
}
