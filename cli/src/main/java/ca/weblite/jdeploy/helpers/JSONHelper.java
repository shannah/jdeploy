package ca.weblite.jdeploy.helpers;

import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class JSONHelper {

    public <T> T getAs(Map<String,?> jsonObjectMap, String key, Class<T> clazz, T defaultValue) {
        if (key.contains(".")) {
            String firstKey = key.substring(0, key.indexOf("."));
            String remainingKey = key.substring(key.indexOf(".") + 1);
            if (!jsonObjectMap.containsKey(firstKey)) {
                return defaultValue;
            }
            Object value = jsonObjectMap.get(firstKey);
            if (!(value instanceof Map)) {
                return defaultValue;
            }
            return getAs((Map<String,?>)value, remainingKey, clazz, defaultValue);
        }
        if (!jsonObjectMap.containsKey(key)) {
            return defaultValue;
        }

        Object value = jsonObjectMap.get(key);
        if (clazz.isInstance(value)) {
            return (T) value;
        }

        if (clazz == String.class) {
            return (T) value.toString();
        }

        if (clazz == Integer.class) {
            return (T) Integer.valueOf(value.toString());
        }

        if (clazz == Boolean.class) {
            return (T) Boolean.valueOf(value.toString());
        }

        if (clazz == Double.class) {
            return (T) Double.valueOf(value.toString());
        }

        if (clazz == Float.class) {
            return (T) Float.valueOf(value.toString());
        }

        if (clazz == Long.class) {
            return (T) Long.valueOf(value.toString());
        }

        if (clazz == Short.class) {
            return (T) Short.valueOf(value.toString());
        }

        if (clazz == Byte.class) {
            return (T) Byte.valueOf(value.toString());
        }

        if (clazz == JSONObject.class) {
            return (T) new JSONObject(value.toString());
        }

        return defaultValue;
    }

    public <T> T getAs(JSONObject jsonObject, String key, Class<T> clazz, T defaultValue) {
        if (key.contains(".")) {
            String firstKey = key.substring(0, key.indexOf("."));
            String remainingKey = key.substring(key.indexOf(".") + 1);
            if (!jsonObject.has(firstKey)) {
                return defaultValue;
            }
            Object firstValue = jsonObject.get(firstKey);
            if (!(firstValue instanceof JSONObject)) {
                return null;
            }
            return getAs((JSONObject) firstValue, remainingKey, clazz, defaultValue);
        }
        if (!jsonObject.has(key)) {
            return defaultValue;
        }

        Object value = jsonObject.get(key);
        if (clazz.isInstance(value)) {
            return (T) value;
        }

        if (clazz == String.class) {
            return (T) value.toString();
        }

        if (clazz == Integer.class) {
            return (T) Integer.valueOf(value.toString());
        }

        if (clazz == Boolean.class) {
            return (T) Boolean.valueOf(value.toString());
        }

        if (clazz == Double.class) {
            return (T) Double.valueOf(value.toString());
        }

        if (clazz == Float.class) {
            return (T) Float.valueOf(value.toString());
        }

        if (clazz == Long.class) {
            return (T) Long.valueOf(value.toString());
        }

        if (clazz == Short.class) {
            return (T) Short.valueOf(value.toString());
        }

        if (clazz == Byte.class) {
            return (T) Byte.valueOf(value.toString());
        }

        return defaultValue;
    }

}
