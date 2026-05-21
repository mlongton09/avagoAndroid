package com.avago.core.data;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class DatabaseFactory_Factory implements Factory<DatabaseFactory> {
  private final Provider<Context> contextProvider;

  public DatabaseFactory_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DatabaseFactory get() {
    return newInstance(contextProvider.get());
  }

  public static DatabaseFactory_Factory create(Provider<Context> contextProvider) {
    return new DatabaseFactory_Factory(contextProvider);
  }

  public static DatabaseFactory newInstance(Context context) {
    return new DatabaseFactory(context);
  }
}
