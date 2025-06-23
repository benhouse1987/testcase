package com.example.mindmap.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

    /**
     * Adds the MyBatis Plus interceptor with pagination support.
     * This is necessary for Page queries (like selectPage) to work.
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // Add the pagination interceptor
        // Specify your database type.
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL)); // Changed to MYSQL
        return interceptor;
    }
}
