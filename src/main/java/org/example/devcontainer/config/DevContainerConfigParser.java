package org.example.devcontainer.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a devcontainer.json (which is JSONC: allows {@code //} and {@code /* *\/} comments and
 * trailing commas) into a {@link DevContainerConfig}. Uses the Gson classes already bundled
 * with the IntelliJ Platform - no extra JSON dependency is needed.
 */
public final class DevContainerConfigParser {

    private DevContainerConfigParser() {
    }

    public static DevContainerConfig parse(Path devcontainerJsonFile) throws IOException {
        String raw = Files.readString(devcontainerJsonFile, StandardCharsets.UTF_8);
        String cleaned = removeTrailingCommas(stripJsonComments(raw));
        JsonObject root = JsonParser.parseString(cleaned).getAsJsonObject();

        DevContainerConfig config = new DevContainerConfig();
        config.name = getString(root, "name");
        config.image = getString(root, "image");

        if (root.has("build") && root.get("build").isJsonObject()) {
            JsonObject b = root.getAsJsonObject("build");
            DevContainerConfig.Build build = new DevContainerConfig.Build();
            build.dockerfile = getString(b, "dockerfile");
            String context = getString(b, "context");
            if (context != null) {
                build.context = context;
            }
            config.build = build;
        }

        config.dockerComposeFile = getStringList(root, "dockerComposeFile");
        config.service = getString(root, "service");
        config.workspaceFolder = getString(root, "workspaceFolder");
        config.postCreateCommand = getStringOrStringList(root, "postCreateCommand");
        config.containerEnv = getStringMap(root, "containerEnv");
        config.forwardPorts = getStringList(root, "forwardPorts");
        config.runArgs = getStringList(root, "runArgs");

        return config;
    }

    private static String getString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        return el.isJsonPrimitive() ? el.getAsString() : null;
    }

    /** Normalizes a field that may be a single string or a JSON array of strings/numbers. */
    private static List<String> getStringList(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        List<String> result = new ArrayList<>();
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                result.add(e.getAsString());
            }
        } else if (el.isJsonPrimitive()) {
            result.add(el.getAsString());
        }
        return result;
    }

    /** postCreateCommand may be a single string (run via shell) or an array (run as argv). */
    private static Object getStringOrStringList(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        if (el.isJsonArray()) {
            List<String> list = new ArrayList<>();
            for (JsonElement e : el.getAsJsonArray()) {
                list.add(e.getAsString());
            }
            return list;
        }
        if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        return null;
    }

    private static Map<String, String> getStringMap(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonObject()) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject(key).entrySet()) {
            map.put(entry.getKey(), entry.getValue().getAsString());
        }
        return map;
    }

    /** Strips {@code //} and {@code /* *\/} comments, leaving string contents untouched. */
    static String stripJsonComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inString = false;
        boolean escaped = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
            } else if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
            } else if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Removes trailing commas before {@code }} or {@code ]}, leaving string contents untouched. */
    static String removeTrailingCommas(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == ',') {
                int j = i + 1;
                while (j < src.length() && Character.isWhitespace(src.charAt(j))) {
                    j++;
                }
                if (j < src.length() && (src.charAt(j) == '}' || src.charAt(j) == ']')) {
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
