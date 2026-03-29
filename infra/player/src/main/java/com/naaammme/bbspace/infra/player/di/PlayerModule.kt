package com.naaammme.bbspace.infra.player.di

import com.naaammme.bbspace.infra.player.Media3PlayerEngine
import com.naaammme.bbspace.infra.player.PlayerEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    @Binds
    @Singleton
    abstract fun bindPlayerEngine(impl: Media3PlayerEngine): PlayerEngine
}
