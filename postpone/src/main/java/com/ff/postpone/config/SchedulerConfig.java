package com.ff.postpone.config;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author Demo-Liu
 * @create 2020-06-12 11:20
 * @description 配置定时任务
 */
@Component
@Configuration
@ConfigurationProperties
@Order(2)
public class SchedulerConfig implements CommandLineRunner, SchedulerFactoryBeanCustomizer {

    private static final Logger log = LoggerFactory.getLogger(SchedulerConfig.class);

    private static final String JOB_NAME = "name";
    private static final String JOB_CLASS = "job";
    private static final String JOB_PARAM = "param";
    private static final String JOB_ACTIVE = "active";
    private static final String JOB_CRON = "cron";

    private static List<Map<String, Object>> jobList;

    private final ApplicationContext applicationContext;

    public SchedulerConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 注册配置文件中的定时任务
     */
    @Override
    @SuppressWarnings("unchecked")
    public void run(String... args) throws Exception {
        log.info("======================================计划任务初始化开始======================================");

        Scheduler scheduler = applicationContext.getBean(Scheduler.class);

        Class<? extends Job> job;
        String jobName;
        String cron;
        Boolean active;
        JobDetail jobDetail;
        Trigger trigger;

        // 清除原有任务
        scheduler.clear();

        if (jobList == null || jobList.isEmpty()) {
            log.info("未配置任何计划任务");
            log.info("======================================计划任务初始化完毕======================================");
            return;
        }

        for (Map<String, Object> map : jobList) {
            active = Boolean.parseBoolean(String.valueOf(map.get(JOB_ACTIVE)));
            jobName = map.get(JOB_NAME) == null ? "" : map.get(JOB_NAME).toString();
            job = (Class<? extends Job>) Class.forName(map.get(JOB_CLASS).toString());

            if (active) {
                JobDataMap jdm = new JobDataMap();
                if (map.get(JOB_PARAM) != null) {
                    jdm.putAll((Map<String, ?>) map.get(JOB_PARAM));
                }

                jobDetail = JobBuilder
                        .newJob(job)
                        .withIdentity(job.getName())
                        .withDescription(jobName)
                        .usingJobData(jdm)
                        .build();

                cron = map.get(JOB_CRON).toString();

                trigger = TriggerBuilder
                        .newTrigger()
                        .withIdentity(job.getName())
                        .withDescription(jobName)
                        .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                        .build();

                scheduler.scheduleJob(jobDetail, trigger);
                log.info("{}({})任务注册成功...", jobName, job.getName());
            } else {
                log.info("{}({})任务未开启...", jobName, job.getName());
            }
        }

        scheduler.start();
        log.info("======================================计划任务初始化完毕======================================");
    }

    /**
     * 自定义 quartz 配置
     */
    @Override
    public void customize(SchedulerFactoryBean schedulerFactoryBean) {
        schedulerFactoryBean.setDataSource(null);
        schedulerFactoryBean.setStartupDelay(3);
    }

    public void setJobList(List<Map<String, Object>> jobList) {
        SchedulerConfig.jobList = jobList;
    }
}