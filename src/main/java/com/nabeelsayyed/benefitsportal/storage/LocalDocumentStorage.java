package com.nabeelsayyed.benefitsportal.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps documents in a folder on disk. Used for local development and tests. */
@Component
@ConditionalOnProperty(name = "documents.storage", havingValue = "local", matchIfMissing = true)
public class LocalDocumentStorage implements DocumentStorage {

    private final Path root;

    public LocalDocumentStorage(@Value("${documents.local-dir:uploads}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save document " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read document " + key, e);
        }
    }

    /** Refuses any key that would land outside the uploads folder (for example "../../etc"). */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Bad document key: " + key);
        }
        return target;
    }
}
