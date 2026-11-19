package com.ff.postpone.constant;


import com.ff.postpone.common.CommonCode;

import java.util.HashMap;
import java.util.Map;

/**
 *  resource文件夹 文件绝对路径
 */
public class ResourceAbPath {

    /**
     * resources 目录路径
     */
    private final static String RESOURCE_PATH = CommonCode.class.getResource("/").getPath();


    /**
     * 私钥
     */
    public static String PRIVATE_KEY_ABPATH = RESOURCE_PATH + ResourcePath.PRIVATE_KEY_PATH;

    /**
     * 延期软文截图绝对路径。
     * key = {@link BlogArticle#getPicPath()}，value = 对应的绝对路径。
     * 此处先给出默认值（IDE 直跑 / 非 jar 打包场景）；
     * jar 打包时由 InitConfig 解压到临时目录后覆盖。
     */
    public static final Map<String, String> PIC_ABPATHS = new HashMap<>();

    static {
        for (BlogArticle article : BlogArticle.values()) {
            PIC_ABPATHS.put(article.getPicPath(), RESOURCE_PATH + article.getPicPath());
        }
    }

    /**
     * 截图js
     */
    public static String PIC_JS_ABPATH = RESOURCE_PATH + ResourcePath.PIC_JS_PATH;

    /**
     * 持久化文件
     */
    public static String PERMANENT_ABPATH = RESOURCE_PATH + ResourcePath.PERMANENT_PATH;


}
