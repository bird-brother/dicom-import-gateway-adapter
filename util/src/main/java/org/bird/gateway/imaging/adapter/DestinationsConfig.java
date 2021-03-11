package org.bird.gateway.imaging.adapter;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @date 11:16 2021-3-1
 * @description TODO
 */
@Slf4j
public class DestinationsConfig {

    public static final String ENV_DESTINATION_CONFIG_JSON = "ENV_DESTINATION_CONFIG_JSON";
    private HashMap<String, String> map = new LinkedHashMap<>();


    /**
     * Creates DestinationsConfig based on provided path to json or environment variable
     * if both parameters absent, will try ENV_DESTINATIONS_JSON
     * @params jsonInline checked 1st
     * @params jsonPath checked 2nd
     * @return DestinationsConfig
     * @since 2021-3-1 13:16
     */
    public DestinationsConfig(String jsonInline, String jsonPath) throws IOException {
        JSONArray jsonArray = JsonUtil.parseConfig(jsonInline, jsonPath, ENV_DESTINATION_CONFIG_JSON);

        if(jsonArray != null) {
            for (Object elem : jsonArray) {
                JSONObject elemJson = (JSONObject) elem;
                String filter = elemJson.getString("filter");
                if(map.containsKey(filter)){
                    throw new IllegalArgumentException(
                            "Duplicate filter in Destinations config. Use --send_to_all_matching_destinations for multiple destination filtering mode.");
                }

                map.put(filter, elemJson.getString("dicomweb_destination"));
            }
        }

        log.info("DestinationsConfig map = {}", map);
    }


    public Map<String, String> getMap() {
        return map;
    }






}
