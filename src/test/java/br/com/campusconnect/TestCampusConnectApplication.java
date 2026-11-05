package br.com.campusconnect;

import org.springframework.boot.SpringApplication;

public class TestCampusConnectApplication {

    public static void main(String[] args) {
        SpringApplication.from(CampusConnectApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
