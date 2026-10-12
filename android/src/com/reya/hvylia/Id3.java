package com.reya.hvylia;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/** Builds an ID3v2.3 tag: title, artist, album and a front-cover picture. */
final class Id3 {
    private static final Charset UTF16LE = Charset.forName("UTF-16LE");
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    private Id3() { }

    static byte[] tag(String title, String artist, String album, byte[] cover, String coverMime) {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        text(frames, "TIT2", title);
        text(frames, "TPE1", artist);
        text(frames, "TALB", album);
        if (cover != null && cover.length > 0) {
            ByteArrayOutputStream pic = new ByteArrayOutputStream();
            pic.write(0); // ISO-8859-1 description
            byte[] mime = (coverMime == null ? "image/jpeg" : coverMime).getBytes(LATIN1);
            pic.write(mime, 0, mime.length);
            pic.write(0);
            pic.write(3); // front cover
            pic.write(0); // empty description
            pic.write(cover, 0, cover.length);
            frame(frames, "APIC", pic.toByteArray());
        }
        byte[] body = frames.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.length + 10);
        out.write('I'); out.write('D'); out.write('3');
        out.write(3); out.write(0); // version 2.3.0
        out.write(0); // flags
        int n = body.length; // synchsafe size
        out.write((n >> 21) & 0x7F); out.write((n >> 14) & 0x7F); out.write((n >> 7) & 0x7F); out.write(n & 0x7F);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    private static void text(ByteArrayOutputStream frames, String id, String value) {
        if (value == null || value.trim().isEmpty()) return;
        byte[] s = value.trim().getBytes(UTF16LE);
        byte[] data = new byte[3 + s.length];
        data[0] = 1; // UTF-16 with BOM
        data[1] = (byte) 0xFF;
        data[2] = (byte) 0xFE;
        System.arraycopy(s, 0, data, 3, s.length);
        frame(frames, id, data);
    }

    private static void frame(ByteArrayOutputStream frames, String id, byte[] data) {
        byte[] head = id.getBytes(LATIN1);
        frames.write(head, 0, 4);
        int n = data.length; // plain 32-bit size in v2.3
        frames.write((n >>> 24) & 0xFF); frames.write((n >>> 16) & 0xFF); frames.write((n >>> 8) & 0xFF); frames.write(n & 0xFF);
        frames.write(0); frames.write(0);
        frames.write(data, 0, data.length);
    }
}
