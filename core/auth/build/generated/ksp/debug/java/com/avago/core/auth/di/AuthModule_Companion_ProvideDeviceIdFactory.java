package com.avago.core.auth.di;

import com.avago.core.auth.SecureTokenStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class AuthModule_Companion_ProvideDeviceIdFactory implements Factory<String> {
  private final Provider<SecureTokenStore> tokenStoreProvider;

  public AuthModule_Companion_ProvideDeviceIdFactory(
      Provider<SecureTokenStore> tokenStoreProvider) {
    this.tokenStoreProvider = tokenStoreProvider;
  }

  @Override
  public String get() {
    return provideDeviceId(tokenStoreProvider.get());
  }

  public static AuthModule_Companion_ProvideDeviceIdFactory create(
      Provider<SecureTokenStore> tokenStoreProvider) {
    return new AuthModule_Companion_ProvideDeviceIdFactory(tokenStoreProvider);
  }

  public static String provideDeviceId(SecureTokenStore tokenStore) {
    return Preconditions.checkNotNullFromProvides(AuthModule.Companion.provideDeviceId(tokenStore));
  }
}
