package org.appspot.apprtc.sound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.support.v4.media.session.MediaSessionCompat;

import org.appspot.apprtc.R;

import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;

public class SoundPlayer implements MediaPlayer.OnCompletionListener {

    MediaPlayer mPlayer = null;
    Context mContext;
    int mDelay = 2000;
    boolean mIsPaused = false;
    AudioManager am;
    boolean mLooping = false;
    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            startAudio();
        }
    };

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                // Pause the playback
            }
        }
    }
    private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private BecomingNoisyReceiver myNoisyAudioStreamReceiver = new BecomingNoisyReceiver();

    MediaSessionCompat.Callback callback = new
            MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    mContext.registerReceiver(myNoisyAudioStreamReceiver, intentFilter);
                }

                @Override
                public void onStop() {
                    mContext.unregisterReceiver(myNoisyAudioStreamReceiver);
                }
            };

    AudioManager.OnAudioFocusChangeListener afChangeListener =
        new AudioManager.OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    // Permanent loss of audio focus
                    // Pause playback immediately
                    mPlayer.pause();
                    mIsPaused = true;
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    // Pause playback
                    mPlayer.pause();
                    mIsPaused = true;
                } else if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    // Lower the volume, keep playing
                    mPlayer.setVolume(0.5f, 0.5f);
                } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    // Your app has been granted audio focus again
                    // Raise volume to normal, restart playback if necessary
                    mPlayer.setVolume(1.0f, 1.0f);
                    if (mIsPaused) {
                        mPlayer.start();
                    }
                }
            }
        };

    public SoundPlayer(Context context, int id) {
        mContext = context;
        am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mPlayer = MediaPlayer.create(context, id);
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.setOnCompletionListener(this);

    }

    @Override
    public void onCompletion(MediaPlayer mp) {

        if (mLooping) {
            Message msg = new Message();
            mHandler.sendEmptyMessageDelayed(0, mDelay);
        }
        // Abandon audio focus when playback completes
        am.abandonAudioFocus(afChangeListener);
    }

    public void Play(boolean loop) {
        mLooping = loop;

        startAudio();
    }

    void startAudio() {
        // Request audio focus for playback
        int result = am.requestAudioFocus(afChangeListener,
                // Use the music stream.
                AudioManager.STREAM_MUSIC,
                // Request permanent focus.
                AudioManager.AUDIOFOCUS_GAIN);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // Start playback
            mPlayer.start();
        }
    }

    public void Stop() {
        mPlayer.stop();
        mHandler.removeMessages(0);

        // Abandon audio focus when playback completes
        am.abandonAudioFocus(afChangeListener);
    }


}