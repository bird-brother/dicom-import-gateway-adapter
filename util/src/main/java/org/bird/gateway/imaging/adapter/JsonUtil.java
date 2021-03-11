package org.bird.gateway.imaging.adapter;

import org.json.JSONArray;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @date 9:22 2021-3-1
 * @description TODO
 */
public class JsonUtil {

    public static JSONArray parseConfig(String jsonInline, String jsonPath, String jsonEnvKey) throws IOException {

        JSONArray result = null;

        if (jsonInline != null && jsonInline.length() > 0) {
            result = new JSONArray(jsonInline);
        } else if (jsonPath != null && jsonPath.length() > 0) {
            result = new JSONArray(new String(
                    Files.readAllBytes(Paths.get(jsonPath)), StandardCharsets.UTF_8));
        } else {
            String jsonEnvValue = System.getenv(jsonEnvKey);
            if (jsonEnvValue != null) {
                result = new JSONArray(jsonEnvValue);
            }
        }

        return result;

    }

}
