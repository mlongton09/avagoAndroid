package com.avago.core.auth;

import android.content.Context;
import com.avago.core.network.AvagoServiceClient;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class IdentityManager_Factory implements Factory<IdentityManager> {
  private final Provider<Context> appContextProvider;

  private final Provider<SecureTokenStore> tokenStoreProvider;

  private final Provider<AvagoServiceClient> serviceClientProvider;

  public IdentityManager_Factory(Provider<Context> appContextProvider,
      Provider<SecureTokenStore> tokenStoreProvider,
      Provider<AvagoServiceClient> serviceClientProvider) {
    this.appContextProvider = appContextProvider;
    this.tokenStoreProvider = tokenStoreProvider;
    this.serviceClientProvider = serviceClientProvider;
  }

  @Override
  public IdentityManager get() {
    return newInstance(appContextProvider.get(), tokenStoreProvider.get(), serviceClientProvider);
  }

  public static IdentityManager_Factory create(Provider<Context> appContextProvider,
      Provider<SecureTokenStore> tokenStoreProvider,
      Provider<AvagoServiceClient> serviceClientProvider) {
    return new IdentityManager_Factory(appContextProvider, tokenStoreProvider, serviceClientProvider);
  }

  public static IdentityManager newInstance(Context appContext, SecureTokenStore tokenStore,
      Provider<AvagoServiceClient> serviceClientProvider) {
    return new IdentityManager(appContext, tokenStore, serviceClientProvider);
  }
}
