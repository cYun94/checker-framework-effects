package org.checkerframework.checker.genericeffects;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import org.checkerframework.checker.genericeffects.qual.IOEffect;
import org.checkerframework.checker.genericeffects.qual.NoIOEffect;

/**
 * Test Class to test IO Effect Checker inside Generic Effect Checker
 *
 * <p>Creates and checks relationship among the valid effects of IO Effect Checker
 */
public final class DefaultEffects extends FlowInsensitiveEffectLattice<Class<? extends Annotation>> {

    /**
     * Method to check Less than equal to Effect
     *
     * @param left : Left Effect
     * @param right: Right Effect
     * @return boolean true : if bottom effect is left effect and is equal to NoIOEffect OR if top
     *     effect is right effect and is equal to IOEffect OR if left effect and right effect are
     *     the same
     *     <p>false : otherwise
     */
    @Override
    public Class<? extends Annotation> LUB(Class<? extends Annotation> left, Class<? extends Annotation> right) {
        assert (left != null && right != null);

        if (left.equals(IOEffect.class) || right.equals(IOEffect.class)) {
            return IOEffect.class;
        } else {
            return NoIOEffect.class;
        }
    }

    /**
     * Get the collection of valid effects. For IO EFfect checker: Valid Effects: IOEffect, and
     * NoIOEffect
     */
    @Override
    public ArrayList<Class<? extends Annotation>> getValidEffects() {

        ArrayList<Class<? extends Annotation>> listOfEffects = new ArrayList<>();
        listOfEffects.add(NoIOEffect.class);
        listOfEffects.add(IOEffect.class);

        return listOfEffects;
    }

    /**
     * Get the Bottom Most Effect of Lattice. For IO EFfect checker: Bottom Most Effect of Lattice:
     * NoIOEffect
     */
    @Override
    public Class<? extends Annotation> bottomEffect() {
        return NoIOEffect.class;
    }
}
