package org.bird.gateway.imaging.adapter;

import com.google.api.client.http.*;
import com.google.common.io.CharStreams;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * @date 9:27 2021-3-1
 * @description TODO
 */
@Slf4j
public class GcpMetadataUtil {

    private static final String baseUrl = "http://metadata/computeMetadata/v1/";


    /**
     * Retrieves metadata element specified by path in GCE environment
     * https://cloud.google.com/compute/docs/storing-retrieving-metadata
     * @param path metadata path
     * @since 2021-3-1 13:31
     */
    public static String get(HttpRequestFactory requestFactory, String path) {
        try {
            HttpRequest httpRequest =
                    requestFactory.buildGetRequest(new GenericUrl(baseUrl + path));
            httpRequest.getHeaders().put("Metadata-Flavor", "Google");
            HttpResponse httpResponse = httpRequest.execute();

            if (!httpResponse.isSuccessStatusCode()
                    || httpResponse.getStatusCode() == HttpStatusCodes.STATUS_CODE_NO_CONTENT) {
                log.warn("Failed to get metadata for {} with response {}:{}",
                        path, httpResponse.getStatusCode(), httpResponse.getStatusMessage());
                return null;
            }

            return CharStreams.toString(new InputStreamReader(
                    httpResponse.getContent(), StandardCharsets.UTF_8));
        } catch (UnknownHostException e) {
            log.trace("Not GCP environment, failed to get metadata for {} with exception {}", path, e);
            return null;
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Failed to get metadata for {} with exception {}", path, e);
            return null;
        }
    }



}
