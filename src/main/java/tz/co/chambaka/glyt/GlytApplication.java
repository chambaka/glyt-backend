package tz.co.chambaka.glyt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = MailSenderAutoConfiguration.class)
@ConfigurationPropertiesScan
public class GlytApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlytApplication.class, args);
    }
}
