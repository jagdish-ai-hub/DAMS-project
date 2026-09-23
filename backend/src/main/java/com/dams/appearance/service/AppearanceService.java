package com.dams.appearance.service;

import com.dams.appearance.entity.PlatformSetting;
import com.dams.appearance.repository.PlatformSettingRepository;
import com.dams.common.exception.DamsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The platform UI font. Chosen by Super Admin from a fixed allowlist — the frontend
 * registry (frontend/src/lib/fonts.ts) must carry the same keys. Never a free-text name.
 */
@Service
public class AppearanceService {

    public static final String FONT_KEY = "ui.font";
    public static final String DEFAULT_FONT = "plex";
    /** Keep in step with FONTS in frontend/src/lib/fonts.ts. */
    public static final List<String> ALLOWED_FONTS = List.of("plex", "inter");

    private final PlatformSettingRepository repo;

    public AppearanceService(PlatformSettingRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public String currentFont() {
        return repo.findById(FONT_KEY)
            .map(PlatformSetting::getValue)
            .filter(ALLOWED_FONTS::contains)
            .orElse(DEFAULT_FONT);
    }

    @Transactional
    public String setFont(String font, Long actorId) {
        if (font == null || !ALLOWED_FONTS.contains(font)) {
            throw DamsException.badRequest(
                "Font '" + font + "' is not one of the available fonts " + ALLOWED_FONTS);
        }
        PlatformSetting row = repo.findById(FONT_KEY).orElseGet(() -> new PlatformSetting(FONT_KEY, font));
        row.setValue(font);
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(actorId);
        repo.save(row);
        return font;
    }
}
