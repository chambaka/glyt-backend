package tz.co.chambaka.glyt.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tz.co.chambaka.glyt.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises the {@link ObjectStore} contract through mocks and a simple in-memory implementation. */
class ObjectStoreUsageTest {

    static class InMemoryObjectStore implements ObjectStore {
        final Map<String, byte[]> data = new HashMap<>();

        @Override
        public void put(String key, byte[] bytes, String contentType) {
            data.put(key, bytes);
        }

        @Override
        public byte[] get(String key) {
            byte[] bytes = data.get(key);
            if (bytes == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "File is missing.");
            }
            return bytes;
        }

        @Override
        public String backend() {
            return "memory";
        }
    }

    @Test
    void mockedStoreCapturesPutArguments() {
        ObjectStore store = mock(ObjectStore.class);
        byte[] bytes = {9, 8, 7};

        store.put("photos/1.jpg", bytes, "image/jpeg");

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(store).put(org.mockito.ArgumentMatchers.eq("photos/1.jpg"), captor.capture(), org.mockito.ArgumentMatchers.eq("image/jpeg"));
        assertThat(captor.getValue()).containsExactly(9, 8, 7);
    }

    @Test
    void mockedStoreReturnsStubbedBytesAndBackend() {
        ObjectStore store = mock(ObjectStore.class);
        when(store.get("k")).thenReturn(new byte[]{1});
        when(store.backend()).thenReturn("s3");

        assertThat(store.get("k")).containsExactly(1);
        assertThat(store.backend()).isEqualTo("s3");
    }

    @Test
    void inMemoryStoreHonoursContract() {
        ObjectStore store = new InMemoryObjectStore();

        store.put("a", new byte[]{5}, "text/plain");

        assertThat(store.get("a")).containsExactly(5);
        assertThat(store.backend()).isEqualTo("memory");
        assertThatThrownBy(() -> store.get("zzz")).isInstanceOf(ApiException.class);
    }
}
