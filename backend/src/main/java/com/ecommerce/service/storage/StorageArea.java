package com.ecommerce.service.storage;

/**
 * The two storage tiers, which differ in how they are served:
 *
 * <ul>
 *   <li>{@link #PUBLIC} - product media. Directly servable (local: the /uploads/**
 *       static handler; S3: a public-read object URL).</li>
 *   <li>{@link #PRIVATE} - sensitive evidence (dispute attachments). Never directly
 *       servable; streamed only through party-checked controller endpoints.</li>
 * </ul>
 */
public enum StorageArea {
    PUBLIC,
    PRIVATE
}
