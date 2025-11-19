package com.telemessage.simulators.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class JSONUtils {
    private static ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally

    static {
        // Register the JavaTimeModule
        mapper.registerModule(new JavaTimeModule());
        // Disable writing dates as timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }


    public static String toJSON(Object o)  {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromJSON(String s, Class<T> aClass) throws IOException {
        return mapper.readValue(s, aClass);
    }

    public static <T> T fromJSON(InputStream in, Class<T> aClass) throws IOException {
        return mapper.readValue(in, aClass);
    }

    public static <T> List<T> ListfromJSON(FileInputStream in, Class<T> aClass) throws IOException {
        return mapper.readValue(in, mapper.getTypeFactory().constructCollectionType(List.class, aClass));
    }
}
