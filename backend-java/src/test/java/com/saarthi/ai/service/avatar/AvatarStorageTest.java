package com.saarthi.ai.service.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.UUID;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Upload validation (docs 12 section 15): whitelist, size, magic bytes,
 * UUID names. Pure unit tests - no D-ID, no database.
 */
class AvatarStorageTest {

    @TempDir
    Path tempDir;

    private static final UUID USER = UUID.randomUUID();

    private AvatarStorage storage() {
        return new AvatarStorage(tempDir.toString(), 5, "http://localhost:8080");
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[16];
        for (int i = 0; i < values.length && i < out.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static MultipartFile file(String name, String contentType, byte[] content) {
        return (MultipartFile) Proxy.newProxyInstance(
                AvatarStorageTest.class.getClassLoader(),
                new Class<?>[]{MultipartFile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBytes" -> content;
                    case "getContentType" -> contentType;
                    case "getSize" -> (long) content.length;
                    case "isEmpty" -> content.length == 0;
                    case "getOriginalFilename" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void acceptsJpegPngWebpByMagicBytes() {
        assertEquals(".jpg", AvatarStorage.extensionFor(bytes(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals(".png", AvatarStorage.extensionFor(bytes(0x89, 'P', 'N', 'G')));
        assertEquals(".webp", AvatarStorage.extensionFor(
                bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')));
    }

    @Test
    void rejectsExecutableDisguisedAsImage() {
        // MZ header + image content-type: magic bytes win, backend never calls D-ID.
        assertEquals(null, AvatarStorage.extensionFor(bytes('M', 'Z', 0x90, 0x00)));
        MultipartFile evil = file("photo.png", "image/png", bytes('M', 'Z', 0x90, 0x00));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> storage().savePhoto(evil, USER));
        assertTrue(e.getMessage().contains("doesn't look like a photo"), e.getMessage());
    }

    @Test
    void rejectsWrongContentType() {
        MultipartFile pdf = file("doc.pdf", "application/pdf", bytes(0xFF, 0xD8, 0xFF, 0xE0));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> storage().savePhoto(pdf, USER));
        assertTrue(e.getMessage().contains("JPG, PNG"), e.getMessage());
    }

    @Test
    void rejectsOversize() {
        AvatarStorage tiny = new AvatarStorage(tempDir.toString(), 0, "http://localhost:8080");
        MultipartFile big = file("a.jpg", "image/jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE0));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> tiny.savePhoto(big, USER));
        assertTrue(e.getMessage().contains("too large"), e.getMessage());
    }

    @Test
    void rejectsEmpty() {
        MultipartFile empty = file("a.jpg", "image/jpeg", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> storage().savePhoto(empty, USER));
    }

    @Test
    void storesUnderUuidNameIgnoringOriginal() throws Exception {
        MultipartFile sneaky = file("../../evil.jpg", "image/jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE0));

        String relative = storage().savePhoto(sneaky, USER);

        assertTrue(relative.startsWith("uploads/avatars/" + USER + "/"), relative);
        assertTrue(relative.endsWith(".jpg"), relative);
        assertTrue(!relative.contains("evil"), relative);
        assertTrue(Files.exists(tempDir.resolve(USER.toString())
                .resolve(relative.substring(relative.lastIndexOf('/') + 1))), relative);
        assertTrue(Files.notExists(tempDir.getParent().resolve("evil.jpg")));
    }

    @Test
    void talkAudioRoundtrip() throws Exception {
        String url = storage().saveTalkAudio(new byte[]{0x49, 0x44, 0x33, 0x04});

        assertTrue(url.startsWith("http://localhost:8080/uploads/audio/"), url);
        assertTrue(url.endsWith(".mp3"), url);
        String saved = url.substring(url.lastIndexOf('/') + 1);
        assertTrue(Files.exists(tempDir.getParent().resolve("audio").resolve(saved)), url);
    }
}
