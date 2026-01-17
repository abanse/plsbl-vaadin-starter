package com.hydro.plsbl;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PLS Barrenlager - Spring Boot + Vaadin Application
 *
 * Start mit: mvn spring-boot:run
 * Browser öffnet: http://localhost:8080
 */
@SpringBootApplication
@EnableScheduling  // Für periodische Kran-Status Updates
@Push(PushMode.AUTOMATIC)  // Für Real-time UI Updates - automatischer Push nach ui.access()
@Theme("plsbl")    // Custom Theme
public class PlsblApplication implements AppShellConfigurator {
    
    public static void main(String[] args) {
        SpringApplication.run(PlsblApplication.class, args);
    }
}
