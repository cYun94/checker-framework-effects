package org.checkerframework.checker.genericeffects.qual;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
/**
 * This annotation is meant to be used by classes so the developer may specify which effect to
 * default to.
 */
public @interface ThrownEffect {
  Class<? extends Exception> exception();

  Class<? extends Annotation> behavior();
}
