package org.bird.gateway;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author bird-brother
 * @date 15:39 2021-2-26
 * @description Contains annotations used in util
 */
public class Annotations {

    @BindingAnnotation
    @Target({ FIELD, PARAMETER, METHOD })
    @Retention(RUNTIME)
    public @interface  GatewayAddr {}

}
