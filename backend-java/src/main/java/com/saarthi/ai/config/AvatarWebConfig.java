package com.saarthi.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serves mentor uploads (photos + per-sentence talk audio) over plain GET.
 * Filenames are random UUIDs, so URLs are unguessable; per-user privacy is
 * enforced at the API layer (my-mentor only ever returns the caller's own).
 * D-ID fetches these same URLs as stream source / talk audio.
 */
@Configuration
public class AvatarWebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public AvatarWebConfig(@Value("${saarthi.avatar.upload-dir:uploads/avatars}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path avatars = Path.of(uploadDir).toAbsolutePath().normalize();
        Path audio = avatars.getParent() != null
                ? avatars.getParent().resolve("audio")
                : Path.of("uploads/audio").toAbsolutePath().normalize();
        // Create dirs now: Path.toUri() omits the trailing slash for a
        // non-existent dir, which would make every lookup resolve against
        // the PARENT ("uploads/") instead of "uploads/avatars/".
        // The explicit trailing "/" below removes that startup-order hazard.
        try {
            java.nio.file.Files.createDirectories(avatars);
            java.nio.file.Files.createDirectories(audio);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot create upload dirs", e);
        }
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations("file:" + avatars + "/");
        registry.addResourceHandler("/uploads/audio/**")
                .addResourceLocations("file:" + audio + "/");
    }
}
