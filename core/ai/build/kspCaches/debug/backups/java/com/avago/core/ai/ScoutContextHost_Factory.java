package com.avago.core.ai;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class ScoutContextHost_Factory implements Factory<ScoutContextHost> {
  @Override
  public ScoutContextHost get() {
    return newInstance();
  }

  public static ScoutContextHost_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ScoutContextHost newInstance() {
    return new ScoutContextHost();
  }

  private static final class InstanceHolder {
    private static final ScoutContextHost_Factory INSTANCE = new ScoutContextHost_Factory();
  }
}
