package com.avago.core.auth.di;

import android.content.Context;
import com.avago.core.auth.SecureTokenStore;
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
public final class AuthModule_Companion_ProvideSecureTokenStoreFactory implements Factory<SecureTokenStore> {
  private final Provider<Context> contextProvider;

  public AuthModule_Companion_ProvideSecureTokenStoreFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SecureTokenStore get() {
    return provideSecureTokenStore(contextProvider.get());
  }

  public static AuthModule_Companion_ProvideSecureTokenStoreFactory create(
      Provider<Context> contextProvider) {
    return new AuthModule_Companion_ProvideSecureTokenStoreFactory(contextProvider);
  }

  public static SecureTokenStore provideSecureTokenStore(Context context) {
    return Preconditions.checkNotNullFromProvides(AuthModule.Companion.provideSecureTokenStore(context));
  }
}
