package org.checkerframework.checker.upload;

import java.lang.annotation.Annotation;
import org.checkerframework.checker.genericeffects.EffectQuantale;
import org.checkerframework.checker.genericeffects.GenericEffectChecker;
import org.checkerframework.framework.source.SupportedLintOptions;
import org.checkerframework.framework.source.SupportedOptions;

@SupportedLintOptions({"debugSpew"})
@SupportedOptions({"ignoreEffects", "ignoreErrors", "ignoreWarnings"})
public class UploadChecker extends GenericEffectChecker<Class<? extends Annotation>> {

  @Override
  public EffectQuantale<Class<? extends Annotation>> getEffectLattice() {
    if (lattice == null) {
      lattice = new UploadEffects();
    }
    return lattice;
  }

  @Override
  public Class<? extends Annotation> fromAnnotation(Class<? extends Annotation> anno) {
    return anno;
  }
}
