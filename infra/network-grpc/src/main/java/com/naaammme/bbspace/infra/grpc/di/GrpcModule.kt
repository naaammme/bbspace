package com.naaammme.bbspace.infra.grpc.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * gRPC 模块 Hilt 配置
 * BiliMetadataBuilder, GrpcHeaderBuilder, BiliGrpcClient 均通过 @Inject constructor 自动提供
 */
@Module
@InstallIn(SingletonComponent::class)
object GrpcModule
