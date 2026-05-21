package com.avago.core.data.repository;

import com.avago.core.data.DatabaseFactory;
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
public final class AssetRepository_Factory implements Factory<AssetRepository> {
  private final Provider<DatabaseFactory> dbFactoryProvider;

  public AssetRepository_Factory(Provider<DatabaseFactory> dbFactoryProvider) {
    this.dbFactoryProvider = dbFactoryProvider;
  }

  @Override
  public AssetRepository get() {
    return newInstance(dbFactoryProvider.get());
  }

  public static AssetRepository_Factory create(Provider<DatabaseFactory> dbFactoryProvider) {
    return new AssetRepository_Factory(dbFactoryProvider);
  }

  public static AssetRepository newInstance(DatabaseFactory dbFactory) {
    return new AssetRepository(dbFactory);
  }
}
