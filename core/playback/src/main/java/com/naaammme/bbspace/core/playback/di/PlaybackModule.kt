package com.naaammme.bbspace.core.playback.di

import com.naaammme.bbspace.core.playback.DownloadPlaybackController
import com.naaammme.bbspace.core.playback.DownloadPlaybackControllerImpl
import com.naaammme.bbspace.core.playback.LivePlaybackController
import com.naaammme.bbspace.core.playback.StreamPlaybackSession
import com.naaammme.bbspace.core.playback.StreamPlaybackSessionImpl
import com.naaammme.bbspace.core.playback.VideoPlaybackController
import com.naaammme.bbspace.core.playback.VideoPlayerRepository
import com.naaammme.bbspace.core.playback.repository.VideoPlayerRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {

    @Binds
    @Singleton
    abstract fun bindVideoPlayerRepository(impl: VideoPlayerRepositoryImpl): VideoPlayerRepository

    @Binds
    @Singleton
    abstract fun bindStreamPlaybackSession(impl: StreamPlaybackSessionImpl): StreamPlaybackSession

    @Binds
    @Singleton
    abstract fun bindVideoPlaybackController(impl: StreamPlaybackSessionImpl): VideoPlaybackController

    @Binds
    @Singleton
    abstract fun bindLivePlaybackController(impl: StreamPlaybackSessionImpl): LivePlaybackController

    @Binds
    @Singleton
    abstract fun bindDownloadPlaybackController(
        impl: DownloadPlaybackControllerImpl
    ): DownloadPlaybackController
}
