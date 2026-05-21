package com.avago.core.data.db;

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
public final class ChatDatabaseFactory_Factory implements Factory<ChatDatabaseFactory> {
  private final Provider<Context> ctxProvider;

  public ChatDatabaseFactory_Factory(Provider<Context> ctxProvider) {
    this.ctxProvider = ctxProvider;
  }

  @Override
  public ChatDatabaseFactory get() {
    return newInstance(ctxProvider.get());
  }

  public static ChatDatabaseFactory_Factory create(Provider<Context> ctxProvider) {
    return new ChatDatabaseFactory_Factory(ctxProvider);
  }

  public static ChatDatabaseFactory newInstance(Context ctx) {
    return new ChatDatabaseFactory(ctx);
  }
}
