package com.njupt.aiassistant;

import com.njupt.aiassistant.entity.DocumentCategory;
import com.njupt.aiassistant.entity.DocumentSourceType;
import com.njupt.aiassistant.entity.DocumentStatus;
import com.njupt.aiassistant.mapper.DocumentMapper;
import com.njupt.aiassistant.mapper.DocumentExclusionMapper;
import com.njupt.aiassistant.entity.DocumentExclusionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CrawlerDocumentFlowIntegrationTests {

    private static final String URL =
            "https://jwc.njupt.edu.cn/2026/0610/c1594a303951/page.htm";
    private static final String HASH_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentExclusionMapper exclusionMapper;

    @Test
    @SuppressWarnings("unchecked")
    void authenticatesAndUpsertsCrawlerDocumentsByUrlAndHash() {
        var unauthorized = post(document(HASH_A, false), "wrong-token");
        assertThat(unauthorized.getStatusCode().value()).isEqualTo(401);

        var created = post(document(HASH_A, false), "test-crawler-token");
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        var createdData = (Map<String, Object>) created.getBody().get("data");
        assertThat(createdData.get("action")).isEqualTo("CREATED");

        var unchanged = post(document(HASH_A, false), "test-crawler-token");
        var unchangedData = (Map<String, Object>) unchanged.getBody().get("data");
        assertThat(unchangedData.get("action")).isEqualTo("UNCHANGED");

        var updated = post(document(HASH_B, false), "test-crawler-token");
        var updatedData = (Map<String, Object>) updated.getBody().get("data");
        assertThat(updatedData.get("action")).isEqualTo("UPDATED");

        var stored = documentMapper.findBySourceUrl(URL).orElseThrow();
        assertThat(stored.getSourceType()).isEqualTo(DocumentSourceType.OFFICIAL_WEBSITE);
        assertThat(stored.getCategory()).isEqualTo(DocumentCategory.ACADEMIC);
        assertThat(stored.getContentHash()).isEqualTo(HASH_B);
        assertThat(stored.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(documentMapper.findAll().stream()
                .filter(document -> URL.equals(document.getSourceUrl())))
                .hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistsAllStaticKnowledgeCategories() {
        var categories = new DocumentCategory[]{
                DocumentCategory.SCHOOL_OVERVIEW,
                DocumentCategory.ORGANIZATION,
                DocumentCategory.RESEARCH
        };
        for (int index = 0; index < categories.length; index++) {
            var category = categories[index];
            var url = "https://www.njupt.edu.cn/static/"
                    + (index + 1) + "/list.htm";
            var response = post(
                    document(
                            String.valueOf(index + 1).repeat(64),
                            false,
                            url,
                            category.name(),
                            "南邮静态知识" + (index + 1)
                    ),
                    "test-crawler-token"
            );
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(documentMapper.findBySourceUrl(url).orElseThrow()
                    .getCategory()).isEqualTo(category);
        }
    }

    @Test
    void acceptsDiscoveredOfficialSubdomainAndRejectsUnsafeAuthority() {
        var officialUrl =
                "https://scie.njupt.edu.cn/2026/0701/c100a1/page.htm";
        var accepted = post(
                document(
                        HASH_A,
                        false,
                        officialUrl,
                        "ACADEMIC",
                        "通信学院教学安排通知"
                ),
                "test-crawler-token"
        );
        assertThat(accepted.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(documentMapper.findBySourceUrl(officialUrl)).isPresent();

        var unsafeUrl =
                "https://crawler@scie.njupt.edu.cn:444/notice/page.htm";
        var rejected = post(
                document(
                        HASH_B,
                        false,
                        unsafeUrl,
                        "ACADEMIC",
                        "不安全来源"
                ),
                "test-crawler-token"
        );
        assertThat(rejected.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @SuppressWarnings("unchecked")
    void excludedSourceCannotBeReingested() {
        var url = "https://cs.njupt.edu.cn/news/noise/page.htm";
        var exclusion = new DocumentExclusionEntity();
        exclusion.setSourceUrl(url);
        exclusion.setReason("管理员删除低价值新闻");
        exclusionMapper.save(exclusion);

        var response = post(
                document(
                        HASH_A,
                        true,
                        url,
                        "ACADEMIC",
                        "学院成功举办教职工活动"
                ),
                "test-crawler-token"
        );

        var data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("action")).isEqualTo("EXCLUDED");
        assertThat(documentMapper.findBySourceUrl(url)).isEmpty();
    }

    private org.springframework.http.ResponseEntity<Map> post(
            Map<String, Object> body,
            String token
    ) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Crawler-Token", token);
        return restTemplate.postForEntity(
                "http://localhost:" + port + "/api/internal/crawler/documents",
                new HttpEntity<>(body, headers),
                Map.class
        );
    }

    private Map<String, Object> document(String hash, boolean forceReindex) {
        return document(
                hash,
                forceReindex,
                URL,
                "ACADEMIC",
                "关于做好2026年转专业工作的通知"
        );
    }

    private Map<String, Object> document(
            String hash,
            boolean forceReindex,
            String url,
            String category,
            String title
    ) {
        var now = OffsetDateTime.now().toString();
        var request = new HashMap<String, Object>();
        request.put("title", title);
        request.put(
                "content",
                "学生申请转专业应按照本科生院通知，在规定时间内提交申请并参加接收学院考核。"
                        + "本段测试内容用于验证官网采集文档的增量入库流程。"
        );
        request.put("source", "南京邮电大学本科生院");
        request.put("source_type", "OFFICIAL_WEBSITE");
        request.put("source_url", url);
        request.put("category", category);
        request.put("published_time", now);
        request.put("crawl_time", now);
        request.put("last_updated", now);
        request.put("content_hash", hash);
        request.put("force_reindex", forceReindex);
        return request;
    }
}
