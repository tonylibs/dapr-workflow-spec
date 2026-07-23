package io.dws.controller.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.serverlessworkflow.api.WorkflowFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the content-addressed version id of a definition. The spec text is parsed
 * into a canonical, key-sorted JSON form first, so semantically identical definitions
 * (whitespace, key order, YAML vs JSON) hash to the same id — the basis for idempotency.
 */
public final class SpecDigest {

    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    private static final int SHORT_LENGTH = 8;

    private SpecDigest() {}

    /** @return {@code v<first 8 hex of sha256(canonical spec)>}. */
    public static String versionId(String specText, WorkflowFormat format) {
        return "v" + sha256Hex(canonicalBytes(specText, format)).substring(0, SHORT_LENGTH);
    }

    public static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] canonicalBytes(String specText, WorkflowFormat format) {
        try {
            Object tree = format.mapper().readValue(specText, Object.class);
            return CANONICAL.writeValueAsBytes(tree);
        } catch (Exception e) {
            // Not canonicalizable (malformed) — hash the raw bytes so callers still get a stable id.
            return specText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
