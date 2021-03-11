package com.bird.gateway.adapter.cmove;

import com.google.common.io.CountingInputStream;
import lombok.extern.slf4j.Slf4j;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.imaging.adapter.DicomClient;
import org.dcm4che3.net.ApplicationEntity;
import org.bird.gateway.IGatewayClient.*;
import org.bird.gateway.imaging.adapter.AetDictionary.*;
import java.io.IOException;
import java.io.InputStream;

/**
 * @date 14:50 2021-3-3
 * @description TODO
 */
@Slf4j
public class CMoveSender implements ISender {

    private final ApplicationEntity applicationEntity;
    private final IGatewayClient gatewayClient;

    public CMoveSender(ApplicationEntity applicationEntity, IGatewayClient gatewayClient) {
        this.applicationEntity = applicationEntity;
        this.gatewayClient = gatewayClient;
    }



    @Override
    public long cmove(Aet target, String studyUid, String seriesUid, String sopInstanceUid, String sopClassUid)
            throws DicomGatewayException, IOException, InterruptedException {
        String wadoUri =
                String.format("studies/%s/series/%s/instances/%s", studyUid, seriesUid, sopInstanceUid);
        log.info("CStore wadoUri : " + wadoUri);

        InputStream responseStream = gatewayClient.wadoRs(wadoUri);

        CountingInputStream countingStream = new CountingInputStream(responseStream);
        DicomClient.connectAndCstore(sopClassUid, sopInstanceUid, countingStream,
                applicationEntity, target.getName(), target.getHost(), target.getPort());
        return countingStream.getCount();
    }

    @Override
    public void close() throws IOException {
        applicationEntity.getDevice().unbindConnections();
    }


}
