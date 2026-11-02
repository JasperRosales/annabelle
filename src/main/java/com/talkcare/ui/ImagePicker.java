package com.talkcare.ui;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Native file chooser for photos (replacing hidden {@code <input type="file">} fields). */
public final class ImagePicker {
    /** PNG/JPEG: profile photos and chat attachments. */
    public static final List<String> PHOTO_TYPES = List.of("image/png", "image/jpeg");
    /** Any image the API accepts for ID documents. */
    public static final List<String> DOCUMENT_TYPES = List.of("image/png", "image/jpeg", "image/gif", "image/webp");
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "gif", "image/gif", "webp", "image/webp");

    private final Window owner;
    private final Dialogs dialogs;

    public ImagePicker(Window owner, Dialogs dialogs) {
        this.owner = owner;
        this.dialogs = dialogs;
    }

    /** Lets the user choose an image of an allowed type and size; empty if cancelled or rejected. */
    public Optional<ImageFile> choose(String title, List<String> allowedTypes) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        List<String> patterns = MIME_BY_EXTENSION.entrySet().stream()
                .filter(e -> allowedTypes.contains(e.getValue()))
                .map(e -> "*." + e.getKey())
                .sorted()
                .toList();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", patterns));
        File file = chooser.showOpenDialog(owner);
        if (file == null) return Optional.empty();

        String mime = MIME_BY_EXTENSION.get(extension(file.getName()));
        if (mime == null || !allowedTypes.contains(mime)) {
            dialogs.error(allowedTypes.equals(PHOTO_TYPES) ? "Please choose a PNG or JPEG photo."
                    : "Please choose an image file.");
            return Optional.empty();
        }
        if (file.length() > MAX_BYTES) {
            dialogs.error("Please choose a photo smaller than 5 MB.");
            return Optional.empty();
        }
        try {
            return Optional.of(new ImageFile(file.getName(), mime, Files.readAllBytes(file.toPath())));
        } catch (IOException e) {
            dialogs.error("The selected photo could not be read.");
            return Optional.empty();
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
