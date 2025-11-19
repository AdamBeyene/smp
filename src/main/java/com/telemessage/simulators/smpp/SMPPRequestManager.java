package com.telemessage.simulators.smpp;

import com.logica.smpp.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SMPPRequestManager {

    // Timeout for waiting for data
    public int timeout = (int)(Data.RECEIVER_TIMEOUT / 3);

    // Map to hold awaiting responses, thread-safe
    private final Map<Integer, Object> awaitingResponse = new ConcurrentHashMap<>();

    // Map to hold locks for synchronization, thread-safe
    private final Map<Integer, Object> locks = new ConcurrentHashMap<>();

    /**
     * Waits for a response associated with a given ID.
     * @param id The ID to wait for.
     * @throws InterruptedException If the thread is interrupted while waiting.
     */
    public void waitForResponse(final Integer id) throws InterruptedException {
        Object lock = locks.computeIfAbsent(id, k -> new Object());
        synchronized (lock) {
            Integer result = get(Integer.class, id);
            if (result != null && result.equals(id)) { // no response yet
                lock.wait(timeout);
            }
        }
    }

    /**
     * Removes the response associated with the given ID from the map.
     * @param <T> The type of the response.
     * @param clazz The class of the expected response type.
     * @param id The ID of the response to remove.
     * @return The removed response cast to type T, or null if not found or not of the correct type.
     */
    public <T> T remove(Class<T> clazz, Integer id) {
        if (id != null) {
            Object responseObj = awaitingResponse.remove(id);
            if (responseObj != null && clazz.isInstance(responseObj)) {
                return clazz.cast(responseObj);
            }
        }
        return null;
    }

    /**
     * Adds a response to the map with the given ID.
     * @param id The ID to associate with the data.
     * @param data The data to store.
     */
    public void put(Integer id, Object data) {
        awaitingResponse.put(id, data);
    }

    /**
     * Puts a response into the map and notifies any threads waiting on this ID.
     * @param id The ID to associate with the data.
     * @param data The data to store.
     */
    public void putAndNotify(Integer id, Object data) {
        put(id, data);
        Object lock = locks.computeIfAbsent(id, k -> new Object());
        synchronized (lock) {
            lock.notify();
        }
    }

    /**
     * Retrieves the response associated with the given ID.
     * @param <T> The type of the response.
     * @param clazz The class of the expected response type.
     * @param id The ID of the response to get.
     * @return The response cast to type T, or null if not found or not of the correct type.
     */
    public <T> T get(Class<T> clazz, Integer id) {
        if (id != null) {
            Object responseObj = awaitingResponse.get(id);
            if (responseObj != null && clazz.isInstance(responseObj)) {
                return clazz.cast(responseObj);
            }
        }
        return null;
    }
}