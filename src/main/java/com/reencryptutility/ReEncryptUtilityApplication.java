package com.reencryptutility;

import com.reencryptutility.service.ReEncrypt;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@ComponentScan(basePackages={ "io.mosip.commons.khazana.*" ,"com.reencryptutility.*"}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASPECTJ,
                pattern = {
                        "io.mosip.commons.khazana.impl.PosixAdapter",
                        "io.mosip.commons.khazana.util.*"
                }) })

/**
 * The Class ReEncryptUtilityApplication.
 * Main class for ReEncryptUtilityApplication
 */
public class ReEncryptUtilityApplication implements CommandLineRunner {

    Logger logger = org.slf4j.LoggerFactory.getLogger(ReEncryptUtilityApplication.class);

    @Autowired
    ReEncrypt reEncrypt;
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext run = SpringApplication.run(ReEncryptUtilityApplication.class, args);
        SpringApplication.exit(run);
    }

    @Override
    public void run(String... args) throws Exception {

        logger.info(" started......");
        reEncrypt.start();
        logger.info("  Completed......");

    }
}
