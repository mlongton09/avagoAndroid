package com.avago.core.ai;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class ScoutViewModel_Factory implements Factory<ScoutViewModel> {
  private final Provider<AiExtractor> extractorProvider;

  private final Provider<ScoutContextHost> contextHostProvider;

  public ScoutViewModel_Factory(Provider<AiExtractor> extractorProvider,
      Provider<ScoutContextHost> contextHostProvider) {
    this.extractorProvider = extractorProvider;
    this.contextHostProvider = contextHostProvider;
  }

  @Override
  public ScoutViewModel get() {
    return newInstance(extractorProvider.get(), contextHostProvider.get());
  }

  public static ScoutViewModel_Factory create(Provider<AiExtractor> extractorProvider,
      Provider<ScoutContextHost> contextHostProvider) {
    return new ScoutViewModel_Factory(extractorProvider, contextHostProvider);
  }

  public static ScoutViewModel newInstance(AiExtractor extractor, ScoutContextHost contextHost) {
    return new ScoutViewModel(extractor, contextHost);
  }
}
