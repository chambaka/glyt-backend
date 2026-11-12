package tz.co.chambaka.glyt.storage;

public interface ObjectStore {
    void put(String key, byte[] bytes, String contentType);

    byte[] get(String key);

    /** Active backend: {@code local} or {@code s3}. */
    String backend();
}
