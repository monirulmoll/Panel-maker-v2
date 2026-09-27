package com.example.engine;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.view.SoundEffectConstants;
import android.view.View;

import java.io.File;

/**
 * Executes custom click and toggle sound triggers configured in the Property Inspector Dock.
 */
public class SoundTriggerPlayer {

    public static final String SOUND_NONE = "NONE";
    public static final String SOUND_CLICK = "SYSTEM_CLICK";
    public static final String SOUND_BEEP = "DIGITAL_BEEP";
    public static final String SOUND_CONFIRM = "CONFIRM_TONE";
    public static final String SOUND_POP = "SWITCH_POP";
    public static final String SOUND_ALERT = "ALERT_PULSE";
    public static final String SOUND_CUSTOM_FILE = "CUSTOM_FILE";

    public static void playSoundTrigger(Context context, View sourceView, String soundType, String customSoundPath) {
        if (soundType == null || SOUND_NONE.equalsIgnoreCase(soundType)) {
            return;
        }

        try {
            if (SOUND_CUSTOM_FILE.equalsIgnoreCase(soundType) && customSoundPath != null && !customSoundPath.trim().isEmpty()) {
                File audioFile = new File(customSoundPath.trim());
                if (audioFile.exists()) {
                    MediaPlayer mp = MediaPlayer.create(context, Uri.fromFile(audioFile));
                    if (mp != null) {
                        mp.setOnCompletionListener(MediaPlayer::release);
                        mp.start();
                        return;
                    }
                }
            }

            if (SOUND_CLICK.equalsIgnoreCase(soundType) && sourceView != null) {
                sourceView.playSoundEffect(SoundEffectConstants.CLICK);
            }

            int toneType;
            int durationMs;
            switch (soundType.toUpperCase()) {
                case SOUND_BEEP:
                    toneType = ToneGenerator.TONE_PROP_BEEP;
                    durationMs = 70;
                    break;
                case SOUND_CONFIRM:
                    toneType = ToneGenerator.TONE_PROP_ACK;
                    durationMs = 110;
                    break;
                case SOUND_POP:
                    toneType = ToneGenerator.TONE_PROP_BEEP2;
                    durationMs = 60;
                    break;
                case SOUND_ALERT:
                    toneType = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD;
                    durationMs = 140;
                    break;
                case SOUND_CLICK:
                default:
                    toneType = ToneGenerator.TONE_SUP_PIP;
                    durationMs = 45;
                    break;
            }

            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            toneGen.startTone(toneType, durationMs);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(toneGen::release, durationMs + 60L);
        } catch (Exception ignored) {
            // Ignore audio hardware exceptions on emulators without audio output
        }
    }
}
