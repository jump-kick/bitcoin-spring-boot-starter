package org.tbk.spring.testcontainer.electrumx.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.tbk.spring.testcontainer.electrumx.ElectrumxContainer;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class ElectrumxContainerApplicationTest {

    @SpringBootApplication
    public static class LndContainerTestApplication {

        public static void main(String[] args) {
            new SpringApplicationBuilder()
                    .sources(LndContainerTestApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(args);
        }
    }

    @Autowired(required = false)
    private ElectrumxContainer<?> electrumxContainer;

    @Test
    public void contextLoads() {
        assertThat(electrumxContainer, is(notNullValue()));
        assertThat(electrumxContainer.isRunning(), is(true));

        Boolean ranForMinimumDuration = Flux.interval(Duration.ofMillis(10))
                .map(foo -> electrumxContainer.isRunning())
                .filter(running -> !running)
                .timeout(Duration.ofSeconds(3), Flux.just(true))
                .blockFirst();

        assertThat("container ran for the minimum amount of time to be considered healthy", ranForMinimumDuration, is(true));
    }
}

