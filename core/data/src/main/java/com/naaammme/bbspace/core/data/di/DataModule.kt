package com.naaammme.bbspace.core.data.di

import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.data.AuthProviderImpl
import com.naaammme.bbspace.core.data.player.VideoPlaybackControllerImpl
import com.naaammme.bbspace.core.data.player.VideoPlaybackSettingsImpl
import com.naaammme.bbspace.core.data.repository.LocalHistoryRepoImpl
import com.naaammme.bbspace.core.data.repository.AuthRepoImpl
import com.naaammme.bbspace.core.data.repository.CommentRepoImpl
import com.naaammme.bbspace.core.data.repository.DanmakuRepoImpl
import com.naaammme.bbspace.core.data.repository.FeedRepoImpl
import com.naaammme.bbspace.core.data.repository.SearchRepoImpl
import com.naaammme.bbspace.core.data.repository.VideoDetailRepoImpl
import com.naaammme.bbspace.core.data.repository.VideoPlayerRepoImpl
import com.naaammme.bbspace.core.domain.auth.AuthRepository
import com.naaammme.bbspace.core.domain.comment.CommentRepository
import com.naaammme.bbspace.core.domain.danmaku.DanmakuRepository
import com.naaammme.bbspace.core.domain.feed.FeedRepository
import com.naaammme.bbspace.core.domain.history.LocalHistoryRepository
import com.naaammme.bbspace.core.domain.player.VideoPlaybackController
import com.naaammme.bbspace.core.domain.player.VideoPlaybackSettings
import com.naaammme.bbspace.core.domain.player.VideoPlayerRepository
import com.naaammme.bbspace.core.domain.search.SearchRepository
import com.naaammme.bbspace.core.domain.video.VideoDetailRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthProvider(impl: AuthProviderImpl): AuthProvider

    @Binds
    @Singleton
    abstract fun bindAuthRepo(impl: AuthRepoImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindFeedRepo(impl: FeedRepoImpl): FeedRepository

    @Binds
    @Singleton
    abstract fun bindCommentRepo(impl: CommentRepoImpl): CommentRepository

    @Binds
    @Singleton
    abstract fun bindDanmakuRepo(impl: DanmakuRepoImpl): DanmakuRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepo(impl: SearchRepoImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindVideoPlayerRepo(impl: VideoPlayerRepoImpl): VideoPlayerRepository

    @Binds
    @Singleton
    abstract fun bindVideoPlaybackController(impl: VideoPlaybackControllerImpl): VideoPlaybackController

    @Binds
    @Singleton
    abstract fun bindVideoPlaybackSettings(impl: VideoPlaybackSettingsImpl): VideoPlaybackSettings

    @Binds
    @Singleton
    abstract fun bindVideoDetailRepo(impl: VideoDetailRepoImpl): VideoDetailRepository

    @Binds
    @Singleton
    abstract fun bindLocalHistoryRepo(impl: LocalHistoryRepoImpl): LocalHistoryRepository
}
