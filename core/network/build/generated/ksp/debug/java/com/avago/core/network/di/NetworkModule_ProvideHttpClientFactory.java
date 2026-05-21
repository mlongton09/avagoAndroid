package com.avago.core.network.di;

import com.avago.core.network.TokenProvider;
import com.avago.core.network.TokenStorage;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.ktor.client.HttpClient;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("javax.inject.Named")
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
public final class NetworkModule_ProvideHttpClientFactory implements Factory<HttpClient> {
  private final Provider<TokenProvider> tokenProvider;

  private final Provider<TokenStorage> tokenStorageProvider;

  private final Provider<String> baseUrlProvider;

  private final Provider<String> deviceIdProvider;

  public NetworkModule_ProvideHttpClientFactory(Provider<TokenProvider> tokenProvider,
      Provider<TokenStorage> tokenStorageProvider, Provider<String> baseUrlProvider,
      Provider<String> deviceIdProvider) {
    this.tokenProvider = tokenProvider;
    this.tokenStorageProvider = tokenStorageProvider;
    this.baseUrlProvider = baseUrlProvider;
    this.deviceIdProvider = deviceIdProvider;
  }

  @Override
  public HttpClient get() {
    return provideHttpClient(tokenProvider.get(), tokenStorageProvider.get(), baseUrlProvider.get(), deviceIdProvider.get());
  }

  public static NetworkModule_ProvideHttpClientFactory create(Provider<TokenProvider> tokenProvider,
      Provider<TokenStorage> tokenStorageProvider, Provider<String> baseUrlProvider,
      Provider<String> deviceIdProvider) {
    return new NetworkModule_ProvideHttpClientFactory(tokenProvider, tokenStorageProvider, baseUrlProvider, deviceIdProvider);
  }

  public static HttpClient provideHttpClient(TokenProvider tokenProvider, TokenStorage tokenStorage,
      String baseUrl, String deviceId) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideHttpClient(tokenProvider, tokenStorage, baseUrl, deviceId));
  }
}
