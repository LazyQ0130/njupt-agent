package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.service.ChatService;
import com.njupt.aiassistant.service.ConversationService;
import com.njupt.aiassistant.service.DocumentService;
import com.njupt.aiassistant.service.FeedbackService;
import com.njupt.aiassistant.service.AdminAuditService;
import com.njupt.aiassistant.service.RateLimitService;
import com.njupt.aiassistant.client.RagClient;
import com.njupt.aiassistant.client.CrawlerClient;
import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.dto.CrawlerScope;
import com.njupt.aiassistant.dto.CustomCrawlerRequest;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.vo.ChatAnswerVO;
import com.njupt.aiassistant.vo.ChatSourceVO;
import com.njupt.aiassistant.vo.DocumentVO;
import com.njupt.aiassistant.vo.DocumentDetailVO;
import com.njupt.aiassistant.vo.RagSearchResultVO;
import com.njupt.aiassistant.vo.CrawlerRunVO;
import com.njupt.aiassistant.vo.CrawlerStatusVO;
import com.njupt.aiassistant.vo.CrawlerTriggerVO;
import com.njupt.aiassistant.vo.DocumentReindexVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        ChatController.class,
        DocumentController.class,
        AdminDocumentController.class,
        AdminCrawlerController.class,
        RagController.class
})
@AutoConfigureMockMvc(addFilters = false)
class ApiControllerTests {

    private static final String SESSION_ID =
            "9dc0ca8e-bdfd-431b-8724-f2eed6a3c877";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private FeedbackService feedbackService;

    @MockitoBean
    private AdminAuditService adminAuditService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private RagClient ragClient;

    @MockitoBean
    private CrawlerClient crawlerClient;

    @Test
    void crawlerStatusUsesFrontendCamelCaseContract() throws Exception {
        var now = OffsetDateTime.parse("2026-07-26T12:00:00+08:00");
        when(crawlerClient.status()).thenReturn(new CrawlerStatusVO(
                false,
                128,
                now,
                new CrawlerRunVO(
                        7L, "COMPLETED", false, now, now,
                        12, 11, 1, 4, 7, 0, null
                )
        ));

        mockMvc.perform(get("/api/admin/crawler"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalWebpages").value(128))
                .andExpect(jsonPath("$.data.latestRun.successCount").value(11))
                .andExpect(jsonPath("$.data.latestRun.forceReindex").value(false))
                .andExpect(jsonPath("$.data.total_webpages").doesNotExist());
    }

    @Test
    void customCrawlerAcceptsOfficialNjuptUrl() throws Exception {
        when(crawlerClient.custom(any())).thenReturn(new CrawlerTriggerVO(
                true,
                8L,
                "定向采集已启动，仅收录近 2 年内容"
        ));

        mockMvc.perform(post("/api/admin/crawler/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seed_url":"https://coa.njupt.edu.cn/2277/list.htm",
                                  "years":2,
                                  "max_pages":50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(8))
                .andExpect(jsonPath("$.data.message")
                        .value("定向采集已启动，仅收录近 2 年内容"));

        var captor = ArgumentCaptor.forClass(CustomCrawlerRequest.class);
        verify(crawlerClient).custom(captor.capture());
        assertEquals(CrawlerScope.EXACT_HOST, captor.getValue().crawlScope());
    }

    @Test
    void customCrawlerForwardsDirectSubdomainScope() throws Exception {
        when(crawlerClient.custom(any())).thenReturn(new CrawlerTriggerVO(
                true,
                9L,
                "学院目录扩展采集已启动"
        ));

        mockMvc.perform(post("/api/admin/crawler/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seed_url":"https://www.njupt.edu.cn/jxjg/list.htm",
                                  "crawl_scope":"DIRECT_NJUPT_SUBDOMAINS",
                                  "years":2,
                                  "max_pages":50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(9));

        var captor = ArgumentCaptor.forClass(CustomCrawlerRequest.class);
        verify(crawlerClient).custom(captor.capture());
        assertEquals(
                CrawlerScope.DIRECT_NJUPT_SUBDOMAINS,
                captor.getValue().crawlScope()
        );
    }

    @Test
    void customCrawlerRejectsExternalUrl() throws Exception {
        mockMvc.perform(post("/api/admin/crawler/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seed_url":"https://example.com/notices",
                                  "years":2,
                                  "max_pages":50
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("仅支持南京邮电大学官方 HTTPS 子域名"));
    }

    @Test
    void askReturnsUnifiedResponse() throws Exception {
        when(chatService.ask(any(), anyString())).thenReturn(new ChatAnswerVO(
                "模拟回答",
                List.of(new ChatSourceVO(
                        "官方文件.pdf",
                        "official",
                        3,
                        0.91,
                        "教务处"
                )),
                98
        ));

        mockMvc.perform(post("/api/chat/ask")
                        .header("X-Anonymous-Session-Id", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"南邮转专业需要什么条件"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").value("模拟回答"))
                .andExpect(jsonPath("$.data.sources[0].type").value("official"))
                .andExpect(jsonPath("$.data.sources[0].page").value(3))
                .andExpect(jsonPath("$.data.sources[0].source").value("教务处"))
                .andExpect(jsonPath("$.data.confidence").value(98));
    }

    @Test
    void askRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/chat/ask")
                        .header("X-Anonymous-Session-Id", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("question: 问题不能为空"));
    }

    @Test
    void historyReturnsList() throws Exception {
        when(chatService.history(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/history")
                        .header("X-Anonymous-Session-Id", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void ragSearchReturnsRetrievedChunksWithoutGeneratingAnswer() throws Exception {
        when(ragClient.search(any())).thenReturn(List.of(
                new RagSearchResultVO(
                        "学生申请转专业应关注教务处当年度通知。",
                        "本科生转专业管理办法.pdf",
                        3,
                        "教务处",
                        0.94
                )
        ));

        mockMvc.perform(post("/api/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"南邮转专业条件","top_k":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].filename").value("本科生转专业管理办法.pdf"))
                .andExpect(jsonPath("$.data[0].page").value(3))
                .andExpect(jsonPath("$.data[0].source").value("教务处"))
                .andExpect(jsonPath("$.data[0].score").value(0.94))
                .andExpect(jsonPath("$.data[0].answer").doesNotExist());
    }

    @Test
    void documentsReturnsList() throws Exception {
        when(documentService.listDocuments()).thenReturn(List.of());

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void documentDetailReturnsContent() throws Exception {
        when(documentService.getDocument(1L)).thenReturn(new DocumentDetailVO(
                1L,
                "本科生转专业管理办法",
                "transfer.pdf",
                "教务处",
                "PDF",
                "文档解析内容",
                com.njupt.aiassistant.entity.DocumentStatus.COMPLETED,
                1024L,
                "application/pdf",
                java.time.LocalDateTime.now()
        ));

        mockMvc.perform(get("/api/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("本科生转专业管理办法"))
                .andExpect(jsonPath("$.data.content").value("文档解析内容"));
    }

    @Test
    void missingDocumentReturnsHttp404() throws Exception {
        when(documentService.getDocument(999L))
                .thenThrow(new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        mockMvc.perform(get("/api/documents/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("文档不存在"));
    }

    @Test
    void uploadAcceptsMultipartFile() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "guide.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes()
        );
        when(documentService.upload(any(), anyString(), anyString()))
                .thenReturn(new DocumentVO(
                        1L,
                        "新生指南",
                        "guide.pdf",
                        "学生工作处",
                        "PDF",
                        com.njupt.aiassistant.entity.DocumentStatus.PROCESSING,
                        file.getSize(),
                        "application/pdf",
                        java.time.LocalDateTime.now()
                ));

        mockMvc.perform(multipart("/api/admin/documents/upload")
                        .file(file)
                        .param("title", "新生指南")
                        .param("source", "学生工作处"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文件上传成功，等待解析"))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }
    @Test
    void reindexSchedulesUploadedDocuments() throws Exception {
        when(documentService.reindexUploadedDocuments())
                .thenReturn(new DocumentReindexVO(3, 2));

        mockMvc.perform(post("/api/admin/documents/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduledCount").value(3))
                .andExpect(jsonPath("$.data.skippedCount").value(2));
    }
}
