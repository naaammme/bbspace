package com.naaammme.bbspace.core.data.di

import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.data.AuthProviderImpl
import com.naaammme.bbspace.core.data.download.VideoDownloadRepoImpl
import com.naaammme.bbspace.core.data.player.DownloadPlaybackControllerImpl
import com.naaammme.bbspace.core.data.player.PlayerSettingsImpl
import com.naaammme.bbspace.core.data.player.StreamPlaybackSessionImpl
import com.naaammme.bbspace.core.data.repository.AuthRepoImpl
import com.naaammme.bbspace.core.data.repository.ArticleRecommendRepoImpl
import com.naaammme.bbspace.core.data.repository.CommentRepoImpl
import com.naaammme.bbspace.core.data.repository.DynamicRepoImpl
import com.naaammme.bbspace.core.data.repository.FeedDislikeRepoImpl
import com.naaammme.bbspace.core.data.repository.FavoriteRepoImpl
import com.naaammme.bbspace.core.data.repository.FeedRepoImpl
import com.naaammme.bbspace.core.data.repository.HistoryRepoImpl
import com.naaammme.bbspace.core.data.repository.LiveRecommendRepoImpl
import com.naaammme.bbspace.core.data.repository.LiveRoomMessageRepoImpl
import com.naaammme.bbspace.core.data.repository.LiveRepoImpl
import com.naaammme.bbspace.core.data.repository.PlaybackHistoryRepoImpl
import com.naaammme.bbspace.core.data.repository.SearchRepoImpl
import com.naaammme.bbspace.core.data.repository.SpaceRepoImpl
import com.naaammme.bbspace.core.data.repository.VodDanmakuRepoImpl
import com.naaammme.bbspace.core.data.repository.VideoDetailRepoImpl
import com.naaammme.bbspace.core.data.repository.ListenRepoImpl
import com.naaammme.bbspace.core.data.repository.VideoPlayerRepoImpl
import com.naaammme.bbspace.core.domain.auth.AuthRepository
import com.naaammme.bbspace.core.domain.article.ArticleRecommendRepository
import com.naaammme.bbspace.core.domain.ImRepository
import com.naaammme.bbspace.core.domain.listen.ListenRepository
import com.naaammme.bbspace.core.domain.comment.CommentRepository
import com.naaammme.bbspace.core.domain.dynamic.DynamicRepository
import com.naaammme.bbspace.core.domain.danmaku.VodDanmakuRepository
import com.naaammme.bbspace.core.domain.download.VideoDownloadRepository
import com.naaammme.bbspace.core.domain.favorite.FavoriteRepository
import com.naaammme.bbspace.core.domain.feed.FeedRepository
import com.naaammme.bbspace.core.domain.feed.FeedDislikeRepository
import com.naaammme.bbspace.core.domain.history.HistoryRepository
import com.naaammme.bbspace.core.domain.history.PlaybackHistoryRepository
import com.naaammme.bbspace.core.domain.history.WatchLaterRepository
import com.naaammme.bbspace.core.domain.live.LiveRecommendRepository
import com.naaammme.bbspace.core.domain.live.LiveRoomMessageRepository
import com.naaammme.bbspace.core.domain.live.LiveRepository
import com.naaammme.bbspace.core.domain.player.DownloadPlaybackController
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.domain.player.StreamPlaybackSession

import com.naaammme.bbspace.core.domain.player.VideoPlayerRepository
import com.naaammme.bbspace.core.domain.search.SearchRepository
import com.naaammme.bbspace.core.domain.space.SpaceRepository
import com.naaammme.bbspace.core.domain.video.VideoDetailRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.naaammme.bbspace.core.data.repository.InterestRepoImpl
import com.naaammme.bbspace.core.data.repository.ImRepoImpl
import com.naaammme.bbspace.core.data.repository.WatchLaterRepoImpl
import com.naaammme.bbspace.core.domain.feed.InterestRepository

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
    abstract fun bindArticleRecommendRepo(impl: ArticleRecommendRepoImpl): ArticleRecommendRepository

    @Binds
    @Singleton
    abstract fun bindFeedRepo(impl: FeedRepoImpl): FeedRepository

    @Binds
    @Singleton
    abstract fun bindFeedDislikeRepo(impl: FeedDislikeRepoImpl): FeedDislikeRepository

    @Binds
    @Singleton
    abstract fun bindFavoriteRepo(impl: FavoriteRepoImpl): FavoriteRepository

    @Binds
    @Singleton
    abstract fun bindLiveRepo(impl: LiveRepoImpl): LiveRepository

    @Binds
    @Singleton
    abstract fun bindLiveRecommendRepo(impl: LiveRecommendRepoImpl): LiveRecommendRepository

    @Binds
    @Singleton
    abstract fun bindLiveRoomMessageRepo(impl: LiveRoomMessageRepoImpl): LiveRoomMessageRepository

    @Binds
    @Singleton
    abstract fun bindCommentRepo(impl: CommentRepoImpl): CommentRepository

    @Binds
    @Singleton
    abstract fun bindDynamicRepo(impl: DynamicRepoImpl): DynamicRepository

    @Binds
    @Singleton
    abstract fun bindVodDanmakuRepo(impl: VodDanmakuRepoImpl): VodDanmakuRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepo(impl: SearchRepoImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindInterestRepo(impl: InterestRepoImpl): InterestRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepo(impl: HistoryRepoImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindWatchLaterRepo(impl: WatchLaterRepoImpl): WatchLaterRepository

    @Binds
    @Singleton
    abstract fun bindSpaceRepo(impl: SpaceRepoImpl): SpaceRepository

    @Binds
    @Singleton
    abstract fun bindVideoPlayerRepo(impl: VideoPlayerRepoImpl): VideoPlayerRepository

    @Binds
    @Singleton
    abstract fun bindStreamPlaybackSession(impl: StreamPlaybackSessionImpl): StreamPlaybackSession


    @Binds
    @Singleton
    abstract fun bindDownloadPlaybackController(
        impl: DownloadPlaybackControllerImpl
    ): DownloadPlaybackController

    @Binds
    @Singleton
    abstract fun bindPlayerSettings(impl: PlayerSettingsImpl): PlayerSettings

    @Binds
    @Singleton
    abstract fun bindVideoDetailRepo(impl: VideoDetailRepoImpl): VideoDetailRepository

    @Binds
    @Singleton
    abstract fun bindVideoDownloadRepo(impl: VideoDownloadRepoImpl): VideoDownloadRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackHistoryRepo(impl: PlaybackHistoryRepoImpl): PlaybackHistoryRepository

    @Binds
    @Singleton
    abstract fun bindListenRepo(impl: ListenRepoImpl): ListenRepository

    @Binds
    @Singleton
    abstract fun bindImRepo(impl: ImRepoImpl): ImRepository
}
