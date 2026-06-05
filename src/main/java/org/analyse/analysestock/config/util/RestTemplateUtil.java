package org.analyse.analysestock.config.util;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;

/**
 * @author dengzhiqiang
 * @date 2022/1/17
 * @describe
 */
@Slf4j
@Component
public class RestTemplateUtil {

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private RestTemplateRetryUtil restTemplateRetryUtil;


    public String postForObject(HashMap<String, Object> map, String url, String prefix) {
        log.info(prefix + "接口请求url：{} 接口请求参数：{}", url, JSONObject.toJSONString(map));
        String result = "";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            //设置请求参数
            HttpEntity<HashMap<String, Object>> request = new HttpEntity<>(map, headers);
            result = restTemplate.postForObject(url, request, String.class);
        } catch (Exception e) {
            log.warn(url + "接口调用异常时返回的结果：{}", result, e);
        }
        if (CharSequenceUtil.isNotEmpty(result)) {
            JSONObject jsonObject = JSONObject.parseObject(result);
            if (jsonObject.getInteger("result") == 1) {
                log.info(url + "接口调用成功：{}", jsonObject.toJSONString());
            } else {
                log.warn(url + "接口调用失败：{}", jsonObject.toJSONString());
                //throw new CommonException(jsonObject.getInteger("result"), jsonObject.getString("msg"));
            }
        }
        return result;
    }

    /**
     * post请求
     *
     * @param jsonData
     * @param url
     * @param prefix
     * @return
     */
    public String postForObject(JSONObject jsonData, String url, String prefix) {
        String jsonString = jsonData.toJSONString();
        log.info(prefix + "接口请求url：{} 接口请求参数：{}", url, jsonString);
        String result = "";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(jsonString, headers);
            result = restTemplate.postForObject(url, request, String.class);
        } catch (Exception e) {
            log.warn(url + "接口调用异常时返回的结果：{}", result, e);
        }
        if (CharSequenceUtil.isNotEmpty(result)) {
            JSONObject jsonObject = JSONObject.parseObject(result);
            if (jsonObject.getInteger("result") == 1) {
                log.info(url + "接口调用成功：{}", jsonObject.toJSONString());
            } else {
                log.warn(url + "接口调用失败：{}", jsonObject.toJSONString());
                //throw new CommonException(jsonObject.getInteger("result"), jsonObject.getString("msg"));
            }
        }
        return result;
    }

    /**
     * post请求重试机制执行
     *
     * @param jsonData
     * @param url
     * @param prefix   备注
     * @return
     */
    public String postForObjectRetry(JSONObject jsonData, String url, String prefix) {
        String jsonString = jsonData.toJSONString();
        log.info(prefix + "接口请求url：{} 接口请求参数：{}", url, jsonString);
        String result = "";
        try {
            result = restTemplateRetryUtil.postForObject(jsonString, url, prefix);
        } catch (Exception e) {
            log.warn(url + "接口调用异常时返回的结果：{}", result, e);
        }
        if (CharSequenceUtil.isNotEmpty(result)) {
            JSONObject jsonObject = JSONObject.parseObject(result);
            if (jsonObject.getInteger("result") == 1) {
                log.info(url + "接口调用成功：{}", jsonObject.toJSONString());
            } else {
                log.info(url + "接口调用失败：{}", jsonObject.toJSONString());
                //throw new CommonException(jsonObject.getInteger("result"), jsonObject.getString("msg"));
            }
        }
        return result;
    }

    /**
     * get请求
     *
     * @param url
     * @param prefix
     * @return
     */
    public String getForObject(String url, String prefix,int isSaveResultLog) {
        log.info("{}接口请求url：{}", prefix, url);
        String result = null;
        try {
            result = restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.warn("{}接口调用异常:{}时返回的结果：{}", url, result, e.getMessage());
            return null;
        }
        if (CharSequenceUtil.isNotBlank(result)) {
            try {
                JSONObject jsonObject = JSON.parseObject(result);
                if (jsonObject.getInteger("result") == 1) {
                    if(isSaveResultLog==1){
                        log.info(url + "接口调用成功：{},返回的结果：{}", url, result);
                    }else{
                        log.info(url + "接口调用成功：{}", url);
                    }
                } else {
                    log.info(url + "接口调用失败：{}", jsonObject.toJSONString());
                    result = null;
                }
            } catch (JSONException e) {
                result = null;
                e.printStackTrace();
                log.info(url + "转json失败：{}", e.getMessage());
            }

        }
        return result;
    }

    /**
     * get请求重试机制
     *
     * @param url
     * @param prefix
     * @return
     */
    public String getForObjectRetry(String url, String prefix,int isSaveResultLog) {
        log.info("{}接口请求url：{}", prefix, url);
        String result = "-1";
        try {
            result = restTemplateRetryUtil.getForObject(url, prefix);
        } catch (Exception e) {
            log.warn("{}接口调用异常:{}时返回的结果：{}", url, result, e.getMessage());
        }
        if (CharSequenceUtil.isNotBlank(result)) {
            try {
                JSONObject jsonObject = JSON.parseObject(result);
                if (jsonObject.getInteger("result") == 1) {
                    if(isSaveResultLog==1){
                        log.info(url + "接口调用成功：{},返回的结果：{}", url, result);
                    }else{
                        log.info(url + "接口调用成功：{}", url);
                    }
                } else {
                    log.info(url + "接口调用失败：{}", jsonObject.toJSONString());
                    result = "-1";
                }

            } catch (JSONException e) {
                result = null;
                e.printStackTrace();
                log.info(url + "转json失败：{}", e.getMessage());
            }
        }
        return result;
    }

    public String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
