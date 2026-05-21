package com.avago.core.network;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class AvagoServiceClient_Factory implements Factory<AvagoServiceClient> {
  private final Provider<HttpClient> clientProvider;

  private final Provider<String> baseUrlProvider;

  public AvagoServiceClient_Factory(Provider<HttpClient> clientProvider,
      Provider<String> baseUrlProvider) {
    this.clientProvider = clientProvider;
    this.baseUrlProvider = baseUrlProvider;
  }

  @Override
  public AvagoServiceClient get() {
    return newInstance(clientProvider.get(), baseUrlProvider.get());
  }

  public static AvagoServiceClient_Factory create(Provider<HttpClient> clientProvider,
      Provider<String> baseUrlProvider) {
    return new AvagoServiceClient_Factory(clientProvider, baseUrlProvider);
  }

  public static AvagoServiceClient newInstance(HttpClient client, String baseUrl) {
    return new AvagoServiceClient(client, baseUrl);
  }
}
