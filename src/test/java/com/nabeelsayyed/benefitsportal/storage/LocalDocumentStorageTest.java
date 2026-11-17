package com.nabeelsayyed.benefitsportal.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDocumentStorageTest {

    @TempDir
    Path dir;

    @Test
    void savesAndReadsAFile() {
        LocalDocumentStorage storage = new LocalDocumentStorage(dir.toString());
        storage.put("claims/1/a.pdf", new byte[]{9, 8, 7}, "application/pdf");

        assertThat(storage.get("claims/1/a.pdf")).containsExactly(9, 8, 7);
    }

    @Test
    void refusesKeysThatEscapeTheFolder() {
        LocalDocumentStorage storage = new LocalDocumentStorage(dir.toString());

        assertThatThrownBy(() -> storage.put("../outside.pdf", new byte[]{1}, "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
