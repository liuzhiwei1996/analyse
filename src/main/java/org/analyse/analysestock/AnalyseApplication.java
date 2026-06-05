package org.analyse.analysestock;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
@MapperScan(
        basePackages = {
                "org.analyse.analysestock.af10product.mapper",
                "org.analyse.analysestock.analysis.mapper"
        },
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
public class AnalyseApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyseApplication.class, args);
    }

}
