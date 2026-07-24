package io.dws.controller.events;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A CloudEvents-style envelope for a DWS lifecycle event (see {@code docs/events.md}).
 *
 * <p>The controller is not replay-constrained, so {@code time} and {@code id} are stamped at the
 * wall-clock boundary here. {@code datacontenttype} is always {@code application/json} and
 * {@code data} is the per-type payload.
 */
public record EventEnvelope(
        String id,
        String source,
        String type,
        String time,
        String datacontenttype,
        Map<String, Object> data) {

    public static final String DATA_CONTENT_TYPE = "application/json";

    public EventEnvelope {
        data = Map.copyOf(data);
    }

    /** Builds an envelope for {@code type} carrying {@code data}, stamping a fresh id and UTC time. */
    public static EventEnvelope create(String type, String source, Map<String, Object> data) {
        return new EventEnvelope(
                UUID.randomUUID().toString(),
                source,
                type,
                Instant.now().toString(),
                DATA_CONTENT_TYPE,
                data);
    }

    /** The envelope as an ordered map, the exact JSON body published to {@code dws.events}. */
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("source", source);
        map.put("type", type);
        map.put("time", time);
        map.put("datacontenttype", datacontenttype);
        map.put("data", data);
        return map;
    }
}
