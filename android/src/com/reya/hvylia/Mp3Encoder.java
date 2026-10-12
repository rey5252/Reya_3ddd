package com.reya.hvylia;

import de.sciss.jump3r.lowlevel.LameEncoder;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

import javax.sound.sampled.AudioFormat;

/** 16-bit little-endian interleaved PCM → constant-bitrate MP3, using jump3r (a pure-Java port of LAME). */
final class Mp3Encoder {
    private final LameEncoder lame;
    private final OutputStream out;
    private final byte[] mp3;
    private final int chunk;

    Mp3Encoder(int sampleRate, int channels, int kbps, OutputStream out) {
        AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels,
                channels * 2, sampleRate, false, new HashMap<String, Object>());
        int mode = channels == 1 ? LameEncoder.CHANNEL_MODE_MONO : LameEncoder.CHANNEL_MODE_JOINT_STEREO;
        lame = new LameEncoder(pcm, kbps, mode, LameEncoder.QUALITY_MIDDLE, false);
        chunk = Math.max(channels * 2, lame.getPCMBufferSize() / (channels * 2) * (channels * 2));
        mp3 = new byte[lame.getMP3BufferSize()];
        this.out = out;
    }

    void write(byte[] pcm, int off, int len) throws IOException {
        while (len > 0) {
            int n = Math.min(len, chunk);
            int m = lame.encodeBuffer(pcm, off, n, mp3);
            if (m > 0) out.write(mp3, 0, m);
            off += n;
            len -= n;
        }
    }

    void finish() throws IOException {
        int m = lame.encodeFinish(mp3);
        if (m > 0) out.write(mp3, 0, m);
        lame.close();
    }
}
