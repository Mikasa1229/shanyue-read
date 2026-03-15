package com.shanyuefang.novel;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.shanyuefang")
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.shanyuefang.novel.mapper")
public class NovelApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovelApplication.class, args);
    }
}
