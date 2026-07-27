package com.njupt.aiassistant.client;

import com.njupt.aiassistant.dto.CustomCrawlerRequest;
import com.njupt.aiassistant.vo.CrawlerStatusVO;
import com.njupt.aiassistant.vo.CrawlerTriggerVO;

public interface CrawlerClient {

    CrawlerStatusVO status();

    CrawlerTriggerVO run();

    CrawlerTriggerVO custom(CustomCrawlerRequest request);

    CrawlerTriggerVO reindex();
}
