package io.materializer.json;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.materializer.shared.utilities.CanonicalJsonUtility;
import io.materializer.shared.utilities.JsonMapperUtility;
import io.materializer.shared.utilities.Preconditions;

public final class JSONDocuments {

    private JSONDocuments() { }

    public static JsonNode parse(String content) {
        try {
            JsonNode value = JsonMapperUtility.MAPPER.readTree(content);
            Preconditions.require(value != null, "JSON document is required");
            return value;
        } catch (IOException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    public static ObjectNode object(JsonNode value, String name) {
        Preconditions.require(value instanceof ObjectNode, name + " must be a JSON object");
        return (ObjectNode) value;
    }

    public static ArrayNode array(JsonNode value, String name) {
        Preconditions.require(value instanceof ArrayNode, name + " must be a JSON list");
        return (ArrayNode) value;
    }

    public static ObjectNode document(Object... pairs) {
        var result = JsonMapperUtility.MAPPER.createObjectNode();
        for (int index = 0; index < pairs.length; index += 2) {
            result.set((String) pairs[index], JsonMapperUtility.MAPPER.valueToTree(pairs[index + 1]));
        }
        return result;
    }

    public static JsonNode value(Object value) {
        return JsonMapperUtility.MAPPER.valueToTree(value);
    }

    public static ArrayNode arrayOf(List<? extends JsonNode> values) {
        return JsonMapperUtility.MAPPER.createArrayNode().addAll(values);
    }

    public static String compact(Object value) {
        return CanonicalJsonUtility.compact(value);
    }

    public static String encode(Object value) {
        return CanonicalJsonUtility.encode(value);
    }
}
