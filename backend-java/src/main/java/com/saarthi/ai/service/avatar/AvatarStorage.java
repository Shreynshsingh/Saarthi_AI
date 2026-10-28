package com.saarthi.ai.service.avatar;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * Mentor upload storage (docs 12 section 15). Every check runs before a byte
 * is kept or D-ID is ever involved: content-type whitelist, size cap, magic
 * bytes (never trust the extension), UUID filenames (no path traversal, no
 * original names on disk). Talk-audio MP3s for D-ID reuse the same layout.
 * All rejections are calm, user-safe IllegalArgumentExceptions (-> HTTP 400).
 */
@Slf4j
@Service
public class AvatarStorage {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path avatarDir;
    private final Path audioDir;
    private final long maxBytes;
    private final String publicBaseUrl;

    public AvatarStorage(
            @Value("${saarthi.avatar.upload-dir:uploads/avatars}") String uploadDir,
            @Value("${saarthi.avatar.max-file-size-mb:5}") long maxFileSizeMb,
            @Value("${saarthi.avatar.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.avatarDir = Path.of(uploadDir).toAbsolutePath().normalize();
        Path parent = this.avatarDir.getParent();
        this.audioDir = parent != null ? parent.resolve("audio") : Path.of("uploads/audio").toAbsolutePath();
        this.maxBytes = maxFileSizeMb * 1024 * 1024;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    /**
     * Validates (present -> content-type -> size -> magic bytes) + stores a
     * mentor photo under {userId}/{UUID}.{ext}. Returns the path relative to
     * the upload root (uploads/avatars/...) so callers build the public URL.
     */
    public String savePhoto(MultipartFile file, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a photo to upload.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("That file won't work — please upload a JPG, PNG, or WebP photo.");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("That photo is too large — please use one under 5MB.");
        }
        byte[] bytes = readBytes(file);
        String extension = extensionFor(bytes);
        if (extension == null) {
            throw new IllegalArgumentException("That file doesn't look like a photo — please try another one.");
        }
        String filename = UUID.randomUUID() + extension;
        Path userDir = avatarDir.resolve(userId.toString());
        writeFile(userDir, filename, bytes);
        log.info("[AvatarStorage] Saved mentor photo for user={} ({} bytes)", userId, bytes.length);
        return "uploads/avatars/" + userId + "/" + filename;
    }

    /** Stores one sentence of talk audio for D-ID. Returns the public URL. */
    public String saveTalkAudio(byte[] mp3) {
        if (mp3 == null || mp3.length == 0) {
            throw new IllegalArgumentException("Empty audio");
        }
        String filename = UUID.randomUUID() + ".mp3";
        writeFile(audioDir, filename, mp3);
        return publicBaseUrl + "/uploads/audio/" + filename;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.warn("[AvatarStorage] Could not read upload");
            throw new IllegalArgumentException("We couldn't read that file — please try another photo.");
        }
    }

    private void writeFile(Path dir, String filename, byte[] bytes) {
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) {
                throw new IllegalArgumentException("That file won't work — please try another one.");
            }
            Files.write(target, bytes);
        } catch (IOException e) {
            log.warn("[AvatarStorage] Could not store upload");
            throw new IllegalArgumentException("We couldn't save that right now — please try again in a moment.");
        }
    }

    /** Magic-byte sniffing: JPEG, PNG, or WebP (RIFF....WEBP). Null = not a photo. */
    static String extensionFor(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return ".jpg";
        }
        if (bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return ".png";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return ".webp";
        }
        return null;
    }
}
