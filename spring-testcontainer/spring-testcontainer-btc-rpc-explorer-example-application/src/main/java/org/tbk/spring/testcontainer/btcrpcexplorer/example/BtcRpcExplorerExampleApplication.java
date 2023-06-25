package org.tbk.spring.testcontainer.btcrpcexplorer.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication(proxyBeanMethods = false)
public class BtcRpcExplorerExampleApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder()
                .sources(BtcRpcExplorerExampleApplication.class)
                .listeners(applicationPidFileWriter())
                .web(WebApplicationType.NONE)
                .profiles("development", "local")
                .run(args);
    }

    public static ApplicationListener<?> applicationPidFileWriter() {
        return new ApplicationPidFileWriter("application.pid");
    }

    @Bean
    ApplicationRunner mainRunner() {
        return args -> {
            log.info("=================================================");
            log.info("Starting...");
            log.info("=================================================");
        };
    }
}
