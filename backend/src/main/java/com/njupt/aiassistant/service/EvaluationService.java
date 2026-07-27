package com.njupt.aiassistant.service;

import com.njupt.aiassistant.dto.EvaluationReviewRequest;
import com.njupt.aiassistant.vo.EvaluationReportVO;

public interface EvaluationService {
    EvaluationReportVO run();
    EvaluationReportVO getReport(String runId);
    EvaluationReportVO review(Long resultId, EvaluationReviewRequest request);
}
