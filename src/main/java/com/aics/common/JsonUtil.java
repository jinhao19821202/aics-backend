package com.aics.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class JsonUtil {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtil() {}

    public static String toJson(Object o) {
        try { return MAPPER.writeValueAsString(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public static <T> T fromJson(String s, Class<T> cls) {
        try { return MAPPER.readValue(s, cls); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public static <T> T fromJson(String s, TypeReference<T> ref) {
        try { return MAPPER.readValue(s, ref); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
