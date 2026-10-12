package com.reya.hvylia;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes any audio Android can play (AAC in M4A, Opus in WebM…) to PCM and re-encodes it as MP3. */
final class AudioTranscoder {
    interface Progress {
        /** @param fraction 0..1 of the source duration */
        void on(float fraction);
        boolean cancelled();
    }

    static final class CancelledException extends IOException {
        CancelledException() { super("cancelled"); }
    }

    private AudioTranscoder() { }

    static void toMp3(String inPath, OutputStream out, int kbps, Progress progress) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(inPath);
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; fmt = f; break; }
            }
            if (track < 0) throw new IOException("no audio track");
            ex.selectTrack(track);
            long durationUs = fmt.containsKey(MediaFormat.KEY_DURATION) ? fmt.getLong(MediaFormat.KEY_DURATION) : 0;
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();

            int sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT;
            Mp3Encoder enc = null;
            byte[] pcm = new byte[0];
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            float lastReported = -1;

            while (!outputDone) {
                if (progress.cancelled()) throw new CancelledException();
                if (!inputDone) {
                    int ii = codec.dequeueInputBuffer(10000);
                    if (ii >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(ii);
                        int n = ex.readSampleData(ib, 0);
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(ii, 0, n, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int oi = codec.dequeueOutputBuffer(info, 10000);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    if (enc == null) {
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmEncoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING);
                } else if (oi >= 0) {
                    if (info.size > 0) {
                        ByteBuffer ob = codec.getOutputBuffer(oi);
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        ob.order(ByteOrder.LITTLE_ENDIAN);
                        int outCh = Math.min(channels, 2);
                        if (enc == null) enc = new Mp3Encoder(sampleRate, outCh, kbps, out);
                        int frames = pcmEncoding == android.media.AudioFormat.ENCODING_PCM_FLOAT
                                ? info.size / (4 * channels) : info.size / (2 * channels);
                        int need = frames * outCh * 2;
                        if (pcm.length < need) pcm = new byte[need];
                        int k = 0;
                        for (int f = 0; f < frames; f++) {
                            for (int c = 0; c < channels; c++) {
                                short s;
                                if (pcmEncoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
                                    float v = ob.getFloat();
                                    s = (short) Math.max(-32768, Math.min(32767, Math.round(v * 32767f)));
                                } else {
                                    s = ob.getShort();
                                }
                                if (c < outCh) { pcm[k++] = (byte) s; pcm[k++] = (byte) (s >> 8); }
                            }
                        }
                        enc.write(pcm, 0, k);
                        if (durationUs > 0) {
                            float fr = Math.min(1f, info.presentationTimeUs / (float) durationUs);
                            if (fr - lastReported >= 0.01f) { lastReported = fr; progress.on(fr); }
                        }
                    }
                    codec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            if (enc == null) throw new IOException("no audio decoded");
            enc.finish();
            progress.on(1f);
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (RuntimeException ignored) { }
                codec.release();
            }
            ex.release();
        }
    }
}
