package com.avago.core.auth;

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
public final class SecureTokenStore_Factory implements Factory<SecureTokenStore> {
  private final Provider<Context> contextProvider;

  public SecureTokenStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SecureTokenStore get() {
    return newInstance(contextProvider.get());
  }

  public static SecureTokenStore_Factory create(Provider<Context> contextProvider) {
    return new SecureTokenStore_Factory(contextProvider);
  }

  public static SecureTokenStore newInstance(Context context) {
    return new SecureTokenStore(context);
  }
}
