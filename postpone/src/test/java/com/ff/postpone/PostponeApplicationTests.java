package com.ff.postpone;

import com.ff.postpone.common.CommonCode;
import com.ff.postpone.constant.ResourceAbPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Objects;

@SpringBootTest
class PostponeApplicationTests {


    @Test
    void contextLoads() {
        String path = Objects.requireNonNull(CommonCode.class.getResource("/")).getPath();
        System.out.println(path);

//        // 直接创建任务类
//        Postpone postpone = new Postpone();
//
//        // 直接调用业务方法（跳过Quartz，直接测试核心逻辑）
//        System.out.println("========== 开始执行延期任务测试 ==========");
//        postpone.postpone();
//        System.out.println("========== 任务执行完成 ==========");
    }
}