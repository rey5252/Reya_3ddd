package javax.sound.sampled;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal stand-in for the Java Sound class that Android does not ship.
 * Only what the jump3r MP3 encoder (de.sciss.jump3r) touches is implemented,
 * with the same behaviour as the JDK class for those members.
 */
public class AudioFormat {

    public static class Encoding {
        public static final Encoding PCM_SIGNED = new Encoding("PCM_SIGNED");

        private final String name;

        public Encoding(String name) {
            this.name = name;
        }

        @Override
        public final boolean equals(Object obj) {
            return obj instanceof Encoding && String.valueOf(name).equals(String.valueOf(((Encoding) obj).name));
        }

        @Override
        public final int hashCode() {
            return name == null ? 0 : name.hashCode();
        }

        @Override
        public final String toString() {
            return name;
        }
    }

    protected Encoding encoding;
    protected float sampleRate;
    protected int sampleSizeInBits;
    protected int channels;
    protected int frameSize;
    protected float frameRate;
    protected boolean bigEndian;
    private final Map<String, Object> properties;

    public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits, int channels,
                       int frameSize, float frameRate, boolean bigEndian) {
        this(encoding, sampleRate, sampleSizeInBits, channels, frameSize, frameRate, bigEndian,
                Collections.<String, Object>emptyMap());
    }

    public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits, int channels,
                       int frameSize, float frameRate, boolean bigEndian, Map<String, Object> properties) {
        this.encoding = encoding;
        this.sampleRate = sampleRate;
        this.sampleSizeInBits = sampleSizeInBits;
        this.channels = channels;
        this.frameSize = frameSize;
        this.frameRate = frameRate;
        this.bigEndian = bigEndian;
        this.properties = new HashMap<String, Object>(properties);
    }

    public Encoding getEncoding() { return encoding; }
    public float getSampleRate() { return sampleRate; }
    public int getSampleSizeInBits() { return sampleSizeInBits; }
    public int getChannels() { return channels; }
    public int getFrameSize() { return frameSize; }
    public float getFrameRate() { return frameRate; }
    public boolean isBigEndian() { return bigEndian; }
    public Map<String, Object> properties() { return Collections.unmodifiableMap(properties); }
    public Object getProperty(String key) { return properties.get(key); }
}
