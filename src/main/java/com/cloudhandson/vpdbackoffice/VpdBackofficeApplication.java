package com.cloudhandson.vpdbackoffice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.cloudhandson.vpdbackoffice.mapper")
@SpringBootApplication
public class VpdBackofficeApplication {

  public static void main(String[] args) {
    SpringApplication.run(VpdBackofficeApplication.class, args);
  }
}
