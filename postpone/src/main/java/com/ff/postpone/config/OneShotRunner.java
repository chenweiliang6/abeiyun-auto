package com.ff.postpone.config;

import com.ff.postpone.task.Postpone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 一次性执行入口（GitHub Actions / 批处理场景）。
 * <p>
 * 仅在激活 {@code oneshot} profile 时生效（默认的 {@code config,permanent,job} 不激活，因此对原常驻 Quartz 模式零侵入）。
 * 关键点：
 * <ul>
 *     <li>{@code @Order(10)} 晚于 {@code InitConfig}（@Order(1)），确保 phantomjs 已解压、Profile.PJ_EXEC 已赋值。</li>
 *     <li>不激活 {@code job} profile 时，{@code SchedulerConfig} 的 jobList 为空，不会 scheduler.start()，Quartz 不启动。</li>
 *     <li>配合启动参数 {@code --spring.main.web-application-type=none} 避开被排除的嵌入式 Tomcat。</li>
 * </ul>
 * 注意：{@link Postpone#postpone()} 内部吞掉单台服务器异常（发邮件后继续下一台），因此本 Runner 的退出码
 * 恒为 0，真实成败需在 GitHub Actions 中通过 grep 日志关键字判定。
 *
 * @author waylon.chen
 */
@Component
@Profile("oneshot")
@Order(10)
public class OneShotRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OneShotRunner.class);

    @Override
    public void run(String... args) {
        log.info("======================================oneshot 延期任务开始======================================");
        Postpone postpone = new Postpone();
        try {
            postpone.postpone();
        } catch (Exception e) {
            log.error("oneshot 延期执行失败", e);
            System.exit(2);
        }
        log.info("======================================oneshot 延期任务完毕======================================");
        System.exit(0);
    }
}
