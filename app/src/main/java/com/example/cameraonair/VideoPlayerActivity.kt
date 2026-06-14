package com.example.cameraonair

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

private const val KEY_POSITION = "video_position"

class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
    }

    private lateinit var videoView: VideoView
    private var savedPosition = 0
    private var isPrepared = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        val uriString = intent.getStringExtra(EXTRA_VIDEO_URI) ?: run {
            finish()
            return
        }

        videoView = findViewById(R.id.videoView)
        savedPosition = savedInstanceState?.getInt(KEY_POSITION) ?: 0

        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)

        videoView.setMediaController(mediaController)
        videoView.setVideoURI(Uri.parse(uriString))
        videoView.setOnPreparedListener {
            isPrepared = true
            videoView.seekTo(savedPosition)
            it.start()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (isPrepared) outState.putInt(KEY_POSITION, videoView.currentPosition)
    }

    override fun onPause() {
        super.onPause()
        if (isPrepared) {
            savedPosition = videoView.currentPosition
            videoView.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isPrepared) {
            videoView.seekTo(savedPosition)
            videoView.start()
        }
    }
}
