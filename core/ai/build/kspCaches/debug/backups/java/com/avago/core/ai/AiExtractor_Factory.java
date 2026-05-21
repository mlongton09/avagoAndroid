package com.avago.core.ai;

import android.content.Context;
import com.avago.core.auth.SecureTokenStore;
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
public final class AiExtractor_Factory implements Factory<AiExtractor> {
  private final Provider<Context> contextProvider;

  private final Provider<AvagoServiceClient> serviceClientProvider;

  private final Provider<SecureTokenStore> tokenStoreProvider;

  public AiExtractor_Factory(Provider<Context> contextProvider,
      Provider<AvagoServiceClient> serviceClientProvider,
      Provider<SecureTokenStore> tokenStoreProvider) {
    this.contextProvider = contextProvider;
    this.serviceClientProvider = serviceClientProvider;
    this.tokenStoreProvider = tokenStoreProvider;
  }

  @Override
  public AiExtractor get() {
    return newInstance(contextProvider.get(), serviceClientProvider.get(), tokenStoreProvider.get());
  }

  public static AiExtractor_Factory create(Provider<Context> contextProvider,
      Provider<AvagoServiceClient> serviceClientProvider,
      Provider<SecureTokenStore> tokenStoreProvider) {
    return new AiExtractor_Factory(contextProvider, serviceClientProvider, tokenStoreProvider);
  }

  public static AiExtractor newInstance(Context context, AvagoServiceClient serviceClient,
      SecureTokenStore tokenStore) {
    return new AiExtractor(context, serviceClient, tokenStore);
  }
}
