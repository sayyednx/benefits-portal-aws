package com.nabeelsayyed.benefitsportal.storage;

/**
 * Where uploaded files are kept. The rest of the app only talks to this interface, so the
 * same code works with files on disk (local development) and with Amazon S3 (in AWS).
 * Which one is used is picked by the documents.storage setting.
 */
public interface DocumentStorage {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);
}
