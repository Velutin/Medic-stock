package com.project.mss.service;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Puts a photo upright before it becomes a PDF page.
 *
 * A phone camera writes the picture in the sensor's own orientation and records how it has to be turned in
 * the EXIF Orientation tag, instead of turning the pixels. Java ignores that tag - ImageIO and OpenPDF read
 * the size from the JPEG frame header and nothing else - so a sheet photographed in portrait arrived in the
 * PDF lying on its side.
 *
 * The tag is read here and the pixels are turned, rather than asking the PDF to rotate the image when it
 * draws it: a rotated image still reports its unrotated size, so the code laying it out on the page would
 * fit it to the wrong box and the picture would come out cropped or small.
 *
 * Nothing is decoded unless there is something to turn. A photo already upright (tag 1, or no tag at all,
 * which is every PNG and most pictures that went through an editor) comes back as the very same bytes, so
 * the usual upload costs nothing and loses no quality.
 */
final class PhotoOrientation {

    private PhotoOrientation() {
    }

    /** EXIF Orientation, as the tag numbers it: 1 is upright, 2 to 8 need turning or mirroring. */
    private static final int TAG_ORIENTATION = 0x0112;
    /** Big pictures are the ones worth not decoding twice; this only guards against an absurd file. */
    private static final int MAX_PIXELS = 80_000_000;

    /**
     * The photo with its pixels upright. Returns the original bytes when there is nothing to do: no EXIF,
     * orientation 1, a format that is not JPEG, or anything that cannot be read. Never throws because of the
     * orientation - a sheet that goes in sideways is a nuisance, a sheet refused is a surgery that cannot be
     * closed.
     */
    static byte[] upright(byte[] photo) {
        int orientation = orientationOf(photo);
        if (orientation <= 1 || orientation > 8) return photo;
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(photo));
            if (source == null) return photo;
            if ((long) source.getWidth() * source.getHeight() > MAX_PIXELS) return photo;
            BufferedImage turned = transform(source, orientation);
            ByteArrayOutputStream out = new ByteArrayOutputStream(photo.length);
            // JPEG: the source is a photo and the page is a scan of paper, so the smaller file is worth more
            // than the last bit of fidelity. ImageIO writes JPEG without the EXIF block, which is what we
            // want here: the pixels are upright now, and a leftover tag would turn them a second time.
            if (!ImageIO.write(turned, "jpg", out)) return photo;
            return out.toByteArray();
        } catch (IOException | RuntimeException cannotTurn) {
            return photo; // includes OutOfMemoryError's cousins and any broken JPEG
        }
    }

    /**
     * Applies the turn the tag asks for. The four mirrored values (2, 4, 5, 7) exist and are handled, even
     * though a camera rarely writes them: a picture that went through an editor can carry one.
     */
    private static BufferedImage transform(BufferedImage src, int orientation) {
        int w = src.getWidth();
        int h = src.getHeight();
        boolean quarterTurn = orientation >= 5; // 5, 6, 7, 8 swap width and height
        int outW = quarterTurn ? h : w;
        int outH = quarterTurn ? w : h;

        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }                                 // mirrored
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }                               // 180
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }                                 // mirrored vertically
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }                             // mirrored + 270
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }                            // 90 clockwise
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(-Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(-Math.PI / 2); }                           // 270 clockwise
            default -> { /* 1: nothing to do, and upright() never gets here */ }
        }

        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_3BYTE_BGR);
        var g = out.createGraphics();
        try {
            g.drawImage(src, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * EXIF Orientation of a JPEG, or 0 when there is none.
     *
     * Walks the JPEG markers looking for APP1 with the "Exif\0\0" header, then reads IFD0 of the TIFF block
     * inside it. Only this one tag is read, so the whole parse is a few dozen bytes; a file that does not
     * look exactly as expected gives up and reports 0 rather than guessing.
     */
    private static int orientationOf(byte[] b) {
        if (b == null || b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) return 0; // not a JPEG
        int i = 2;
        while (i + 3 < b.length) {
            if ((b[i] & 0xFF) != 0xFF) return 0; // out of step with the markers
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { // no length of their own
                i += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) return 0; // pixel data starts: there is no EXIF
            int length = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            if (length < 2 || i + 2 + length > b.length) return 0;
            if (marker == 0xE1 && length >= 8 + 2
                    && b[i + 4] == 'E' && b[i + 5] == 'x' && b[i + 6] == 'i' && b[i + 7] == 'f'
                    && b[i + 8] == 0) {
                return orientationInTiff(b, i + 10, i + 2 + length);
            }
            i += 2 + length;
        }
        return 0;
    }

    /** IFD0 of the TIFF block that starts at {@code start} (the byte-order mark) and ends before {@code end}. */
    private static int orientationInTiff(byte[] b, int start, int end) {
        if (start + 8 > end) return 0;
        boolean bigEndian;
        if (b[start] == 'M' && b[start + 1] == 'M') bigEndian = true;
        else if (b[start] == 'I' && b[start + 1] == 'I') bigEndian = false;
        else return 0;
        if (u16(b, start + 2, bigEndian) != 42) return 0; // the TIFF magic number

        long ifdOffset = u32(b, start + 4, bigEndian);
        int ifd = start + (int) ifdOffset;
        if (ifdOffset < 8 || ifd + 2 > end) return 0;

        int entries = u16(b, ifd, bigEndian);
        for (int n = 0; n < entries; n++) {
            int entry = ifd + 2 + n * 12;
            if (entry + 12 > end) return 0;
            if (u16(b, entry, bigEndian) == TAG_ORIENTATION) {
                // SHORT, one value, written in the first two bytes of the value field
                int value = u16(b, entry + 8, bigEndian);
                return value >= 1 && value <= 8 ? value : 0;
            }
        }
        return 0;
    }

    private static int u16(byte[] b, int i, boolean bigEndian) {
        int a = b[i] & 0xFF;
        int c = b[i + 1] & 0xFF;
        return bigEndian ? (a << 8) | c : (c << 8) | a;
    }

    private static long u32(byte[] b, int i, boolean bigEndian) {
        long a = b[i] & 0xFFL, c = b[i + 1] & 0xFFL, d = b[i + 2] & 0xFFL, e = b[i + 3] & 0xFFL;
        return bigEndian ? (a << 24) | (c << 16) | (d << 8) | e : (e << 24) | (d << 16) | (c << 8) | a;
    }
}
