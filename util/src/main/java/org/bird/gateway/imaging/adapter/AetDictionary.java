package org.bird.gateway.imaging.adapter;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;

/**
 * @date 13:26 2021-3-1
 * @description TODO
 */
@Slf4j
public class AetDictionary {

    private static final String ENV_AETS_JSON = "ENV_AETS_JSON";
    private HashMap<String, Aet> aetMap = new HashMap<>();


    /**
     * Creates AetDictionary based on provided path to json or environment variable
     * if both parameters absent, will try ENV_AETS_JSON
     * @param jsonInline checked 1st
     * @param jsonPath checked 2nd
     * @since 2021-3-1 13:26
     */
    public AetDictionary( String jsonInline, String jsonPath) throws IOException {
        JSONArray jsonArray = JsonUtil.parseConfig(jsonInline, jsonPath, ENV_AETS_JSON);

        if (jsonArray != null) {
            for (Object elem : jsonArray) {
                JSONObject elemJson = (JSONObject) elem;
                String name = elemJson.getString("name");
                aetMap.put(name, new Aet(name, elemJson.getString("host"), elemJson.getInt("port")));
            }
        }

        log.info("aetMap = {}", aetMap);
    }

    public AetDictionary(Aet[] aets) {
        for (Aet elem : aets) {
            aetMap.put(elem.getName(), elem);
        }
    }

    public Aet getAet(String name) {
        return aetMap.get(name);
    }



    public static class Aet {

        private String name;
        private String host;
        private int port;

        public Aet(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        @Override
        public String toString() {
            return "Aet{" +
                    "name='" + name + '\'' +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    "}";
        }
    }

















}
