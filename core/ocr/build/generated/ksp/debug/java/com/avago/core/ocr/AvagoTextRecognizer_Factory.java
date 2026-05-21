package com.avago.core.ocr;

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
public final class AvagoTextRecognizer_Factory implements Factory<AvagoTextRecognizer> {
  @Override
  public AvagoTextRecognizer get() {
    return newInstance();
  }

  public static AvagoTextRecognizer_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static AvagoTextRecognizer newInstance() {
    return new AvagoTextRecognizer();
  }

  private static final class InstanceHolder {
    private static final AvagoTextRecognizer_Factory INSTANCE = new AvagoTextRecognizer_Factory();
  }
}
