package com.pocket.rpg.rendering.postfx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to provide a user-friendly description for a PostEffect.
 * Displayed in the PostEffect browser popup and help tooltips.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EffectDescription {
    String value();
}
