package org.bird.gateway;

import com.google.api.client.http.*;
import com.google.common.io.CharStreams;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.net.Status;
import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @author bird-brother
 * @date 15:52 2021-2-26
 * @description A client for communicating with the DICOM Gateway API.
 */
@Slf4j
public class GatewayClient  implements  IGatewayClient{

    // Factory to create HTTP requests with proper credentials.
    protected  final HttpRequestFactory requestFactory;

    // Service prefix all dicomWeb paths will be appended to.
    private final String serviceUrlPrefix;

    // The path for a StowRS request to be appened to serviceUrlPrefix.
    private final String stowPath;


    public GatewayClient(HttpRequestFactory requestFactory, @Annotations.GatewayAddr String serviceUrlPrefix, String stowPath){
        this.requestFactory = requestFactory;
        this.serviceUrlPrefix = serviceUrlPrefix;
        this.stowPath = stowPath;
    }





    /**
     * Makes a WADO-RS call and returns the response InputStream.
     */
    public InputStream wadoRs(String path) throws IGatewayClient.DicomGatewayException{
        try {
            HttpRequest httpRequest = requestFactory.buildGetRequest(new GenericUrl(serviceUrlPrefix + "/" + StringUtil.trim(path)));
            httpRequest.getHeaders().put("Accept", "application/dicom; transfer-syntax=*");
            HttpResponse httpResponse = httpRequest.execute();
            return httpResponse.getContent();
        }catch (HttpResponseException e) {
            throw new DicomGatewayException(
                    String.format("WADO-RS: %d, %s", e.getStatusCode(), e.getStatusMessage()),
                    e, e.getStatusCode(), Status.ProcessingFailure
            );
        }catch (IOException | IllegalArgumentException e){
            throw  new IGatewayClient.DicomGatewayException(e);
        }
    }



    /**
     * Makes a QIDO-RS call and returns a JSON array.
     */
    public JSONArray qidoRs(String path) throws IGatewayClient.DicomGatewayException{
        try {
            HttpRequest httpRequest = requestFactory.buildGetRequest(new GenericUrl(serviceUrlPrefix + "/" + StringUtil.trim(path)));
            HttpResponse httpResponse = httpRequest.execute();

            // dcm4chee server can return 204 responses.
            if (httpResponse.getStatusCode() == HttpStatusCodes.STATUS_CODE_NO_CONTENT) {
                return new JSONArray();
            }
            return new JSONArray(CharStreams.toString(new InputStreamReader(httpResponse.getContent(), StandardCharsets.UTF_8)));
        }catch (HttpResponseException e){
            throw new DicomGatewayException(String.format("QIDO-RS: %d, %s", e.getStatusCode(), e.getStatusMessage()),
                    e, e.getStatusCode(), Status.UnableToCalculateNumberOfMatches);
        }catch (IOException | IllegalArgumentException e) {
            throw new IGatewayClient.DicomGatewayException(e);
        }
    }



    /**
     * Makes a STOW-RS call.
     * DICOM "Type" parameter:
     * http://dicom.nema.org/medical/dicom/current/output/html/part18.html#sect_6.6.1.1.1
     * @param in The DICOM input stream.
     */
    public void stowRs(InputStream in) throws IGatewayClient.DicomGatewayException{


        GenericUrl url = new GenericUrl(StringUtil.joinPath(serviceUrlPrefix, this.stowPath));

        MultipartContent content = new MultipartContent();
        content.setMediaType(new HttpMediaType("multipart/related; type=\"application/dicom\""));
        content.setBoundary(UUID.randomUUID().toString());
        InputStreamContent dicomStream = new InputStreamContent("application/dicom", in);
        content.addPart(new MultipartContent.Part(dicomStream));


        HttpResponse resp = null;
        try {
            HttpRequest httpRequest = requestFactory.buildPostRequest(url, content);
            httpRequest.setConnectTimeout(15000);
            resp = httpRequest.execute();
            log.info(String.format("StowRs: %d",resp.getStatusCode()));
        }catch (HttpResponseException e){
            throw new DicomGatewayException(String.format("StowRs: %d, %s", e.getStatusCode(), e.getStatusMessage()),
                    e, e.getStatusCode(), Status.ProcessingFailure);
        }catch (IOException e){
            throw new IGatewayClient.DicomGatewayException(e);
        }finally {
            try {
                if((resp) != null){
                    resp.disconnect();
                }
            }catch (IOException e) {
                throw new IGatewayClient.DicomGatewayException(e);
            }
        }



    }
















}
