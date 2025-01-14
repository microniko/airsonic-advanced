/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.primitives.Ints;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static java.util.stream.Collectors.toList;

/**
 * Miscellaneous general utility methods.
 *
 * @author Sindre Mehus
 */
public final class Util {

    private static final Logger LOG = LoggerFactory.getLogger(Util.class);
    private static final String URL_SENSITIVE_REPLACEMENT_STRING = "<hidden>";

    /**
     * Disallow external instantiation.
     */
    private Util() {
    }

    public static boolean isWindows() {
        return SystemUtils.IS_OS_WINDOWS;
    }

    public static <T> List<T> subList(List<T> list, long offset, long max) {
        if (list.size() == Integer.MAX_VALUE) {
            return list.stream().skip(offset).limit(max).collect(toList());
        }
        return list.subList(Math.min(list.size(), Ints.saturatedCast(offset)), Math.min(list.size(), Ints.saturatedCast(offset + max)));
    }

    public static int[] toIntArray(List<Integer> values) {
        if (values == null) {
            return new int[0];
        }
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public static String debugObject(Object object) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            LOG.warn("Cant output debug object", e);
            return "";
        }
    }

    /**
     * Return a complete URL for the given HTTP request,
     * including the query string.
     *
     * @param request An HTTP request instance
     * @return The associated URL
     */
    public static String getURLForRequest(HttpServletRequest request) {
        String url = request.getRequestURL().toString();
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) url += "?" + queryString;
        return url;
    }

    /**
     * Return an URL for the given HTTP request, with anonymized sensitive parameters.
     *
     * @param request An HTTP request instance
     * @return The associated anonymized URL
     */
    public static String getAnonymizedURLForRequest(HttpServletRequest request) {

        String url = getURLForRequest(request);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        MultiValueMap<String, String> components = builder.build().getQueryParams();

        // Subsonic REST API authentication (see RESTRequestParameterProcessingFilter)
        if (components.containsKey("p")) builder.replaceQueryParam("p", URL_SENSITIVE_REPLACEMENT_STRING);  // Cleartext password
        if (components.containsKey("t")) builder.replaceQueryParam("t", URL_SENSITIVE_REPLACEMENT_STRING);  // Token
        if (components.containsKey("s")) builder.replaceQueryParam("s", URL_SENSITIVE_REPLACEMENT_STRING);  // Salt
        if (components.containsKey("u")) builder.replaceQueryParam("u", URL_SENSITIVE_REPLACEMENT_STRING);  // Username

        return builder.build().toUriString();
    }

    /**
     * Return true if the given object is an instance of the class name in argument.
     * If the class doesn't exist, returns false.
     */
    public static boolean isInstanceOfClassName(Object o, String className) {
        try {
            return Class.forName(className).isInstance(o);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            return null;
        }
    }

    public static String toJson(Object object, SerializationFeature feature) {
        try {
            return objectMapper.writer(feature).writeValueAsString(object);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, String> objectToStringMap(Object object) {
        return objectMapper.convertValue(object, new TypeReference<Map<String, String>>() {});
    }

    public static <T> T stringMapToObject(Class<T> clazz, Map<String, String> data) {
        return objectMapper.convertValue(data, clazz);
    }

    private static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    public static <T> T stringMapToValidObject(Class<T> clazz, Map<String, String> data) {
        T object = stringMapToObject(clazz, data);
        Set<ConstraintViolation<T>> validate = validator.validate(object);
        if (validate.isEmpty()) {
            return object;
        } else {
            throw new IllegalArgumentException("Created object was not valid");
        }
    }

    public static ThreadFactory getDaemonThreadfactory(String prefixName) {
        return r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            t.setName(prefixName + "-" + t.getName());
            return t;
        };
    }
}