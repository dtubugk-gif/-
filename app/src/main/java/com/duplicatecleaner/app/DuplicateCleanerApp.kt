package com.duplicatecleaner.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class DuplicateCleanerApp : Application(), ImageLoaderFactory {

    /** Coil image loader shared by every thumbnail; the video decoder renders a frame for videos. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
