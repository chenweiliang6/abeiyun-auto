package com.ff.postpone.constant;

/**
 * @author Demo-Liu
 * @create 2020-08-05 16:30
 * @description 云信息
 */
public enum CloudInfo {


    ABEI("0",
            "阿贝云",
            "https://api.abeiyun.com",
            "https://api.abeiyun.com/www/login.php",
            "https://api.abeiyun.com/www/renew.php",
            "https://api.sanfengyun.com/www/user.php"),
    SANFENG("1",
                 "三丰云",
                 "https://api.sanfengyun.com",
                 "https://api.sanfengyun.com/www/login.php",
                 "https://api.sanfengyun.com/www/renew.php",
                    "https://api.sanfengyun.com/www/user.php");


    String type;
    String cloudName;
    String os;
    String loginUri;
    String busUri;
    String userUri;

    CloudInfo(String type, String cloudName, String os, String loginUri, String busUri, String userUri) {
        this.type = type;
        this.cloudName = cloudName;
        this.os = os;
        this.loginUri = loginUri;
        this.busUri = busUri;
        this.userUri = userUri;
    }

    public static CloudInfo getCloudInfo(String type){
        // type 可能为 null（配置文件 / CI secrets 留空），此处不能省 null 判断，
        // 否则会在 equals 处抛 NPE，导致未配置的服务器无法走"未启用，已跳过"分支。
        if (type == null) {
            return null;
        }
        for (CloudInfo cloudInfo : values()) {
            if(type.equals(cloudInfo.type)){
                return cloudInfo;
            }
        }
        return null;
    }

    public String getType() {
        return type;
    }

    public String getCloudName() {
        return cloudName;
    }

    public String getOs() {
        return os;
    }

    public String getLoginUri() {
        return loginUri;
    }

    public String getBusUri() {
        return busUri;
    }
}
