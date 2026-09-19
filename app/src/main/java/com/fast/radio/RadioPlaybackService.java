package com.fast.radio;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * Background radio playback service for Fast Radio.
 *
 * Low Buffer is intentional for fast station switching.
 * It does not reduce the bitrate of the network stream.
 */
public class RadioPlaybackService extends MediaSessionService {

    private ExoPlayer player;
    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferParameters(
                        5_000,   // 5 seconds minimum buffer
                        10_000,  // 10 seconds maximum buffer
                        1_000,   // 1 second playback-start buffer
                        2_000    // 2 seconds rebuffer buffer
                )
                .build();

        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .build(),
                        true
                )
                .build();

        mediaSession = new MediaSession.Builder(this, player).build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
