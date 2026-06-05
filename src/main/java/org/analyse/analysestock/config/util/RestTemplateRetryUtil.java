package org.analyse.analysestock.config.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * @author dengzhiqiang
 * @date 2022/1/17
 * @describe
 */
@Component
@Slf4j
public class RestTemplateRetryUtil {

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 重试机制
     *
     * @param jsonData
     * @param url
     * @return
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 5000L, multiplier = 1))
    public String postForObject(String jsonData, String url, String prefix) {
        //只是记录次数
        log.info("{}重试执行post请求:", prefix);
        return restTemplate.postForObject(url, jsonData, String.class);
    }

    /**
     * 重试机制
     *
     * @param url
     * @return
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 5000L, multiplier = 2))
    public String getForObject(String url, String prefix) {
        //只是记录次数
        log.info("{}重试执行get请求:",prefix);
        return restTemplate.getForObject(url, String.class);
    }
}