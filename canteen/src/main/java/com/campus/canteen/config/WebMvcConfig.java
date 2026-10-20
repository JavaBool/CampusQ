
package com.campus.canteen.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SessionRefreshInterceptor sessionRefreshInterceptor;

    public WebMvcConfig(
            SessionRefreshInterceptor sessionRefreshInterceptor) {
        this.sessionRefreshInterceptor = sessionRefreshInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionRefreshInterceptor)
                .addPathPatterns("/", "/menu", "/cart", "/profile");
    }
}
