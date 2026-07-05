package com.ecommerce.service.storage;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Object-storage abstraction. Two interchangeable implementations, selected by
 * {@code storage.provider}:
 *
 * <ul>
 *   <li>{@code local} (default) - the filesystem, behaviour-identical to the
 *       original code. No external dependency; fine for single-instance dev.</li>
 *   <li>{@code s3} - MinIO or AWS S3. Required once the backend runs more than one
 *       replica (local disk is not shared). See docs/DEPLOYMENT_PLAYBOOK.md §8.</li>
 * </ul>
 *
 * Keys are forward-slash relative paths, e.g. {@code products/{id}/{file}} or
 * {@code disputes/{id}/{file}}. Callers own the key scheme; this service only
 * stores/reads/deletes bytes.
 */
public interface StorageService {

    /** Persist a multipart upload at {@code key}; resolves to the number of bytes stored. */
    Mono<Long> store(StorageArea area, String key, FilePart file, String contentType);

    /** Stream a stored object's bytes (used by party-checked private-evidence endpoints). */
    Flux<DataBuffer> read(StorageArea area, String key);

    /** Delete a single object. No-op if it does not exist. */
    Mono<Void> delete(StorageArea area, String key);

    /** Delete every object under a key prefix (e.g. all of a product's or dispute's files). */
    Mono<Void> deletePrefix(StorageArea area, String prefix);

    /**
     * A directly-servable URL for a {@link StorageArea#PUBLIC} object. Local returns a
     * relative {@code /uploads/{key}} path (served by the static handler); S3 returns
     * an absolute public URL. Not meaningful for {@link StorageArea#PRIVATE}.
     */
    String publicUrl(String key);
}
