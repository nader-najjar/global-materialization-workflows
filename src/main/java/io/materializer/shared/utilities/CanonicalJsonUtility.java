package io.materializer.shared.utilities;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public final class CanonicalJsonUtility {

    private CanonicalJsonUtility() { }

    public static String compact(Object value) {
        try {
            return JsonMapperUtility.MAPPER.writeValueAsString(value);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static String encode(Object value) {
        return pretty(JsonMapperUtility.MAPPER.valueToTree(value), 0) + "\n";
    }

    private static String pretty(JsonNode value, int depth) {
        if (value == null || value.isNull()) {
            return "null";
        }
        if (value.isObject()) {
            return prettyObject(value, depth);
        }
        if (value.isArray()) {
            return prettyArray(value, depth);
        }
        return compact(value);
    }

    private static String prettyObject(JsonNode value, int depth) {
        var entries = new ArrayList<String>();
        var properties = value.properties().stream().sorted(Comparator.comparing(entry -> entry.getKey())).toList();
        for (var entry : properties) {
            entries.add(indent(depth + 1) + compact(entry.getKey()) + ": " + pretty(entry.getValue(), depth + 1));
        }
        return enclose("{", "}", entries, depth);
    }

    private static String prettyArray(JsonNode value, int depth) {
        var entries = new ArrayList<String>();
        for (JsonNode item : value) {
            entries.add(indent(depth + 1) + pretty(item, depth + 1));
        }
        return enclose("[", "]", entries, depth);
    }

    private static String enclose(String open, String close, List<String> entries, int depth) {
        return entries.isEmpty() ? open + close : open + "\n" + String.join(",\n", entries) + "\n" + indent(depth) + close;
    }

    private static String indent(int depth) {
        return "  ".repeat(depth);
    }
}
