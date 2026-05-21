package com.avago.core.auth;

import android.content.Context;
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
public final class AccountManifest_Factory implements Factory<AccountManifest> {
  private final Provider<Context> appContextProvider;

  public AccountManifest_Factory(Provider<Context> appContextProvider) {
    this.appContextProvider = appContextProvider;
  }

  @Override
  public AccountManifest get() {
    return newInstance(appContextProvider.get());
  }

  public static AccountManifest_Factory create(Provider<Context> appContextProvider) {
    return new AccountManifest_Factory(appContextProvider);
  }

  public static AccountManifest newInstance(Context appContext) {
    return new AccountManifest(appContext);
  }
}
