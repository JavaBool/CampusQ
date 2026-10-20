
package com.campus.canteen.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration registering HTTP handler interceptors.
 *
 * <p>Hooks the {@link SessionRefreshInterceptor} into key customer-facing web routes
 * so session validity and user profile state are verified prior to view rendering.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SessionRefreshInterceptor sessionRefreshInterceptor;

    public WebMvcConfig(
            SessionRefreshInterceptor sessionRefreshInterceptor) {
        this.sessionRefreshInterceptor = sessionRefreshInterceptor;
    }

    /**
     * Registers the {@link SessionRefreshInterceptor} for standard customer-facing navigation paths.
     *
     * @param registry the {@link InterceptorRegistry} to add interceptors to
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionRefreshInterceptor)
                .addPathPatterns("/", "/menu", "/cart", "/profile");
    }
}
