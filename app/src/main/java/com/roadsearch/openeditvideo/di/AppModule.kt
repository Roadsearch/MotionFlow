package com.roadsearch.openeditvideo.di

import android.content.Context
import androidx.work.WorkManager
import com.roadsearch.openeditvideo.data.ProjectRepository
import com.roadsearch.openeditvideo.media.MediaEngine
import com.roadsearch.openeditvideo.export.Media3VideoExporter
import com.roadsearch.openeditvideo.export.VideoExporter
import com.roadsearch.openeditvideo.export.AdvancedFfmpegVideoExporter
import com.roadsearch.openeditvideo.export.RoutedVideoExporter
import com.roadsearch.openeditvideo.export.ReflectiveFfmpegBridge
import com.roadsearch.openeditvideo.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideProjectRepository(@ApplicationContext context: Context): ProjectRepository = ProjectRepository(context)

    @Provides @Singleton
    fun provideMediaEngine(@ApplicationContext context: Context): MediaEngine = MediaEngine(context)

    @Provides @Singleton
    fun provideVideoExporter(@ApplicationContext context: Context): VideoExporter =
        RoutedVideoExporter(
            media3 = Media3VideoExporter(context),
            advanced = AdvancedFfmpegVideoExporter(context),
            ffmpegAvailable = { ReflectiveFfmpegBridge().isAvailable() },
        )

    @Provides @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository = SettingsRepository(context)

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
