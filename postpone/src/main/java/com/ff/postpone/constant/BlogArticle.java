package com.ff.postpone.constant;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 延期软文：一篇「文章链接」+ 与之对应的「文章截图」。
 * <p>
 * 阿贝云续期时要求提交软文 url 和文章截图（yanqi_img），两者必须对应。
 * 为避免每 5 天提交同一份内容被判重，这里维护 4 组，按北京时间日期轮换使用。
 *
 * @author waylon.chen
 */
public enum BlogArticle {

    TIEBA("https://tieba.baidu.com/p/11189155364",
            ResourcePath.TIEBA_PIC_PATH),

    ZHIHU("https://zhuanlan.zhihu.com/p/2104185894471312688",
            ResourcePath.ZHIHU_PIC_PATH),

    CSDN("https://blog.csdn.net/m0_59822086/article/details/145536699",
            ResourcePath.CSDN_PIC_PATH),

    JUEJIN("https://juejin.cn/post/7687209773676118042",
            ResourcePath.JUEJIN_PIC_PATH);

    /**
     * 文章链接，作为 renew.php 的 url 字段
     */
    private final String url;

    /**
     * 文章截图在 resources 下的相对路径
     */
    private final String picPath;

    BlogArticle(String url, String picPath) {
        this.url = url;
        this.picPath = picPath;
    }

    public String getUrl() {
        return url;
    }

    public String getPicPath() {
        return picPath;
    }

    /**
     * 按日期轮换选取。
     * <p>
     * 索引 = 北京时间 epochDay % 组数。定时任务每 5 天执行一次，而 5 % 4 == 1，
     * 因此每次执行索引必然 +1，遍历完 4 组才回到起点，不会连续两次使用同一组；
     * 同一天重复执行（手动补跑）则取到同一组，保证幂等。
     *
     * @param date 用于选取的日期（按北京时间传入）
     */
    public static BlogArticle pickByDate(LocalDate date) {
        BlogArticle[] values = values();
        int idx = (int) Math.floorMod(date.toEpochDay(), values.length);
        return values[idx];
    }

    /**
     * 按北京时间「今天」选取
     */
    public static BlogArticle pickToday() {
        return pickByDate(LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }
}
