package com.example.mindmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.example.mindmap.mapper")
public class MindmapApplication {

	public static void main(String[] args) {
		SpringApplication.run(MindmapApplication.class, args);
	}

}
