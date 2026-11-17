package pe.yupana;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YupanaApplication {
    public static void main(String[] args) {
        SpringApplication.run(YupanaApplication.class, args);
    }
}
