package com.naaammme.bbspace.core.auth.di

import com.naaammme.bbspace.core.auth.AuthProviderImpl
import com.naaammme.bbspace.core.common.AuthProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthProvider(impl: AuthProviderImpl): AuthProvider
}
