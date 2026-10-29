package com.bookstore.ecommerce.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Single time source. Tests replace it with a fixed or movable clock to check the 48-hour window. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Money is always a JSON string with exactly two decimals, never a number (docs/api-conventions.md). */
    @Bean
    Module moneyModule() {
        SimpleModule module = new SimpleModule("money");
        module.addSerializer(BigDecimal.class, new JsonSerializer<>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(value.setScale(2, RoundingMode.HALF_UP).toPlainString());
            }
        });
        return module;
    }
}
