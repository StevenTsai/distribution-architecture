package com.godzilla.distribution.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for ops-only API controllers.
 *
 * <p>Combined with {@code OpsApiCondition} configuration class,
 * which activates these controllers only when {@code app.api.role=ops}.</p>
 *
 * @see com.godzilla.distribution.common.config.OpsApiCondition
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpsApi {
}
