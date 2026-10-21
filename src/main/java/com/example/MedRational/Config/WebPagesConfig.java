package com.example.MedRational.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Public pages served from resources/static. /delete-account is the account deletion link given to
// Google Play, so it must work without the app.
@Configuration
public class WebPagesConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/delete-account").setViewName("forward:/delete-account.html");
    }
}
