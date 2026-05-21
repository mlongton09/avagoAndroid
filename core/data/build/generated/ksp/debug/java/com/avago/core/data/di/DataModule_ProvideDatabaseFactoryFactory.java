package com.avago.core.data.di;

import android.content.Context;
import com.avago.core.data.DatabaseFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class DataModule_ProvideDatabaseFactoryFactory implements Factory<DatabaseFactory> {
  private final Provider<Context> contextProvider;

  public DataModule_ProvideDatabaseFactoryFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DatabaseFactory get() {
    return provideDatabaseFactory(contextProvider.get());
  }

  public static DataModule_ProvideDatabaseFactoryFactory create(Provider<Context> contextProvider) {
    return new DataModule_ProvideDatabaseFactoryFactory(contextProvider);
  }

  public static DatabaseFactory provideDatabaseFactory(Context context) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideDatabaseFactory(context));
  }
}
