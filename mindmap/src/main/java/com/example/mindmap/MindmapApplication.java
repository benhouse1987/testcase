package com.example.mindmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Added to enable scheduled tasks
@MapperScan("com.example.mindmap.mapper")
public class MindmapApplication {

	public static void main(String[] args) {
		SpringApplication.run(MindmapApplication.class, args);
	}

}
