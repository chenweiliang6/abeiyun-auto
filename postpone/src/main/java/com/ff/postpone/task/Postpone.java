package com.ff.postpone.task;

import com.alibaba.fastjson2.JSONObject;
import com.ff.postpone.common.CommonCode;
import com.ff.postpone.constant.*;
import com.ff.postpone.util.*;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.core5.http.HttpEntity;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DisallowConcurrentExecution
public class Postpone extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(Postpone.class);

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        postpone();
    }

    public void postpone() {
        List<Map<String, String>> cloudServers = Profile.cloudServers;

        for (Map<String, String> serverInfo : cloudServers) {
            try {
                processCloudServer(serverInfo);
            } catch (Exception e) {
                log.error("服务器延期过程出错", e);
                handleException(serverInfo, e);
            }
        }
    }

    private void processCloudServer(Map<String, String> serverInfo) throws Exception {
        String username = serverInfo.get(Constans.CLOUD_USERNAME);
        String type = serverInfo.get(Constans.CLOUD_TYPE);
        boolean enable = Boolean.parseBoolean(serverInfo.get(Constans.ENABLE));

        CloudInfo cloudInfo = CloudInfo.getCloudInfo(type);
        if (!enable || cloudInfo == null) {
            // 未启用，或 type 为空/非法（如对应 secret 未配置）时直接跳过，避免 NPE
            String cloudName = cloudInfo != null ? cloudInfo.getCloudName() : ("type=" + (type == null ? "" : type));
            log.warn("{}未启用，已跳过", cloudName);
            return;
        }

        String cloudName = cloudInfo.getCloudName();
        String ukLog = CommonCode.getUKLog(username, cloudName);
        String uKey = CommonCode.getUserKey(username, type);

        BasicCookieStore cookieStore = new BasicCookieStore();
        HttpClient httpClient = HttpUtil.getHttpClient(cookieStore);
        Map<String, String> cookieMap = new LinkedHashMap<>();

        String status = loginAndCheck(httpClient, cookieStore, cookieMap, serverInfo, cloudInfo, ukLog, uKey);
        if ("1".equals(status)) {
            executeDelayProcess(httpClient, cookieStore, cookieMap, cloudInfo, ukLog, username, cloudName);
        }
    }

    public String loginAndCheck(HttpClient httpClient,
                                BasicCookieStore cookieStore,
                                Map<String, String> cookieMap,
                                Map<String, String> serverInfo,
                                CloudInfo cloudInfo,
                                String ukLog,
                                String uKey) throws Exception {

        String username = serverInfo.get(Constans.CLOUD_USERNAME);
        String password = serverInfo.get(Constans.CLOUD_PASSWORD);
        String cloudName = cloudInfo.getCloudName();
        String type = cloudInfo.getType();

        Map<String, String> userInfo = loadUserInfo(uKey);
        String nextTime = userInfo.get(CloudDataKey.NEXT_TIME);

        if (StringUtil.isEmpty(nextTime) || CommonCode.isExpire(nextTime)) {
            log.info("{}开始登录...", ukLog);
            return performLoginAndCheck(httpClient, cookieStore, cookieMap, username, password,
                    cloudInfo, cloudName, type, ukLog, uKey, userInfo);
        } else {
            log.info("{}未到期", ukLog);
            return null;
        }
    }

    private Map<String, String> loadUserInfo(String uKey) {
        Map<String, String> userInfo = Profile.userInfos.get(uKey);
        if (userInfo == null) {
            userInfo = new HashMap<>();
        }
        return userInfo;
    }

    private String performLoginAndCheck(HttpClient httpClient, BasicCookieStore cookieStore,
                                        Map<String, String> cookieMap,
                                        String username, String password,
                                        CloudInfo cloudInfo, String cloudName, String type,
                                        String ukLog, String uKey, Map<String, String> userInfo) throws Exception {

        JSONObject loginJson = executeLoginRequest(httpClient, cookieStore, cookieMap, cloudInfo.getLoginUri(),
                username, password, ukLog);

        if (!CloudDataKey.LOGIN_SUCCESS.equals(loginJson.getString(CloudDataKey.LOGIN_STATUS))) {
            log.error("{}登录失败", ukLog);
            return null;
        }

        if (!HttpUtil.hasEssentialCookies(cookieMap)) {
            log.warn("{}登录成功，但关键 Cookie 不完整: {}", ukLog, HttpUtil.buildCookieHeader(cookieMap));
        } else {
            log.info("{}登录成功，关键 Cookie 已就绪", ukLog);
        }

        log.info("{}登录成功，开始检查免费服务器状态", ukLog);

        // 登录成功，调用状态接口
        JSONObject statusJson = checkServerStatus(httpClient, cookieStore, cookieMap, cloudInfo.getBusUri(), ukLog);
        String status = extractServerStatus(statusJson);

        // 审核状态接口
        JSONObject checkJson = queryCheckRecords(httpClient, cookieStore, cookieMap, cloudInfo.getBusUri(), ukLog);

        return processServerStatus(status, statusJson, checkJson, ukLog, uKey, type,
                userInfo.get("blogUrl"));
    }

    private JSONObject executeLoginRequest(HttpClient httpClient, BasicCookieStore cookieStore,
                                           Map<String, String> cookieMap,
                                           String loginUri, String username, String password, String ukLog)
            throws IOException, URISyntaxException {

        log.info("{}执行登录请求", ukLog);

        String response = HttpUtil.getPostResAndCaptureCookies(
                httpClient,
                loginUri,
                CloudPostParams.getYunLogin(username, password),
                null,
                cookieMap
        );

        JSONObject loginJson = JSONUtils.parseObject(response);
        log.info("{}登录接口返回:{}", ukLog, loginJson);
        log.info("{}登录后 Cookie:{}", ukLog, HttpUtil.buildCookieHeader(cookieMap));

        return loginJson;
    }

    private JSONObject checkServerStatus(HttpClient httpClient, BasicCookieStore cookieStore,
                                         Map<String, String> cookieMap,
                                         String busUri, String ukLog) throws IOException, URISyntaxException {

        String response = HttpUtil.getPostRes(
                httpClient,
                busUri,
                CloudPostParams.getFreeStatus(),
                HttpUtil.buildHeadersWithCookies(null, cookieMap)
        );

        JSONObject statusJson = JSONUtils.parseObject(response);
        log.info("{}检查服务器状态返回:{}", ukLog, statusJson);

        return statusJson;
    }

    private JSONObject queryCheckRecords(HttpClient httpClient, BasicCookieStore cookieStore,
                                         Map<String, String> cookieMap,
                                         String busUri, String ukLog) throws IOException, URISyntaxException {

        String response = HttpUtil.getPostRes(
                httpClient,
                busUri,
                CloudPostParams.getCheckStatus(),
                HttpUtil.buildHeadersWithCookies(null, cookieMap)
        );

        JSONObject checkJson = JSONUtils.parseObject(response);
        log.info("{}延期记录接口返回:{}", ukLog, checkJson);

        return checkJson;
    }

    private String extractServerStatus(JSONObject statusJson) {
        JSONObject msgJson = JSONUtils.getObject(statusJson, CloudDataKey.STATUS_DATA);

        if (JSONUtils.containsKey(msgJson, CloudDataKey.DELAY_STATUS1)) {
            return JSONUtils.getString(msgJson, CloudDataKey.DELAY_STATUS1);
        } else if (JSONUtils.containsKey(msgJson, CloudDataKey.DELAY_STATUS2)) {
            return JSONUtils.getString(msgJson, CloudDataKey.DELAY_STATUS2);
        }

        return null;
    }

    private String processServerStatus(String status, JSONObject statusJson, JSONObject checkJson,
                                       String ukLog, String uKey, String type,
                                       String blogUrl) throws Exception {

        switch (status) {
            case "1":
                CommonCode.checkCheckStatus(checkJson, ukLog, blogUrl);
                log.info("{}已到审核期", ukLog);
                break;

            case "0":
                // 未到审核期
                CommonCode.checkCheckStatus(checkJson, ukLog, blogUrl);
                String nextTime = JSONUtils.getString(JSONUtils.getObject(statusJson, CloudDataKey.STATUS_DATA), CloudDataKey.NEXT_TIME);

                Map<String, String> userInfo = loadUserInfo(uKey);
                userInfo.put(CloudDataKey.NEXT_TIME, nextTime);
                CommonCode.userInfosPermanent(uKey, userInfo);
                log.info("{}未到审核期，下次审核开始时间:{}", ukLog, nextTime);
                break;

            default:
                log.info("{}正在审核", ukLog);
                break;
        }

        return status;
    }

    /**
     * 执行延期：按日期轮换选取一组「软文链接 + 对应截图」，直接提交 renew.php。
     * 不再发布/等待/截图/删除博客。
     */
    private void executeDelayProcess(HttpClient httpClient, BasicCookieStore cookieStore,
                                     Map<String, String> cookieMap,
                                     CloudInfo cloudInfo,
                                     String ukLog, String username, String cloudName) throws IOException {

        BlogArticle article = BlogArticle.pickToday();
        String picAbPath = ResourceAbPath.PIC_ABPATHS.get(article.getPicPath());
        File screenshotFile = new File(picAbPath);

        log.info("{}开始提交延期（文章:{}，配图:{}）", ukLog, article.getUrl(), screenshotFile.getName());
        if (!screenshotFile.exists()) {
            log.warn("{}配图不存在:{}", ukLog, picAbPath);
        }
        String response = submitBlogInfo(httpClient, cookieStore, cookieMap, cloudInfo, screenshotFile, article.getUrl(), ukLog);
        handleSubmissionResult(response, cloudName, username, ukLog);
    }

    private String submitBlogInfo(HttpClient httpClient, BasicCookieStore cookieStore,
                                  Map<String, String> cookieMap,
                                  CloudInfo cloudInfo, File screenshotFile, String blogUrl, String ukLog)
            throws IOException {

        log.info("{}提交延期博客信息 url:{}", ukLog, blogUrl);
        HttpEntity entity = CloudPostParams.getBlogInfo(cloudInfo, screenshotFile, blogUrl).build();

        return HttpUtil.getPostRes(
                httpClient,
                cloudInfo.getBusUri(),
                entity,
                HttpUtil.buildHeadersWithCookies(null, cookieMap)
        );
    }

    private void handleSubmissionResult(String response, String cloudName,
                                        String username, String ukLog) {

        JSONObject json = JSONUtils.parseObject(response);
        log.info("{}提交延期记录返回结果：{}", ukLog, json);

        if (CloudDataKey.BLOG_SUCCESS.equals(json.getString(CloudDataKey.BLOG_STATUS))) {
            log.info("{}提交延期记录成功", ukLog);
        } else {
            log.error("{}提交延期记录失败", ukLog);
        }
    }

    private void handleException(Map<String, String> serverInfo, Exception e) {
        String username = serverInfo.get(Constans.CLOUD_USERNAME);
        String type = serverInfo.get(Constans.CLOUD_TYPE);
        CloudInfo cloudInfo = CloudInfo.getCloudInfo(type);

        if (cloudInfo != null) {
            String cloudName = cloudInfo.getCloudName();
            String ukLog = CommonCode.getUKLog(username, cloudName);
            log.error("{}延期过程出错", ukLog, e);
        } else {
            log.error("处理服务器配置时出错：{}", serverInfo, e);
        }
    }
}
