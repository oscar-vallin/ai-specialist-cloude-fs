package com.bookstore.ecommerce.config;

import com.bookstore.ecommerce.common.CallerArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CallerArgumentResolver callerResolver;

    WebConfig(CallerArgumentResolver callerResolver) {
        this.callerResolver = callerResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(callerResolver);
    }
}
