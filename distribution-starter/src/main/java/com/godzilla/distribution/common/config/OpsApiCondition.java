package com.godzilla.distribution.common.config;

import com.godzilla.distribution.common.annotation.OpsApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Activates {@code @OpsApi}-annotated controllers only when {@code app.api.role=ops}.
 *
 * <p>This separates the conditional logic from the marker annotation,
 * keeping {@code @OpsApi} as a pure marker.</p>
 */
@Configuration
@ConditionalOnProperty(name = "app.api.role", havingValue = "ops")
@ComponentScan(
        basePackages = "com.godzilla.distribution.controller",
        includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = OpsApi.class),
        useDefaultFilters = false
)
public class OpsApiCondition {
}
