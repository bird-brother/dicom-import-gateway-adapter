package com.bird.gateway.adapter.cstore.multipledest.sender;

import org.bird.gateway.imaging.adapter.DicomClient;
import org.dcm4che3.net.ApplicationEntity;
import org.bird.gateway.imaging.adapter.AetDictionary.*;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * @date 14:59 2021-3-2
 * @description TODO
 */
public class CStoreSender  implements Closeable {

    private final ApplicationEntity applicationEntity;


    public CStoreSender(ApplicationEntity applicationEntity) {
        this.applicationEntity = applicationEntity;
    }


    public void cstore(Aet target, String sopInstanceUid, String sopClassUid,
                       InputStream inputStream) throws IOException, InterruptedException {
        DicomClient.connectAndCstore(
                sopClassUid,
                sopInstanceUid,
                inputStream,
                applicationEntity,
                target.getName(),
                target.getHost(),
                target.getPort());
    }

    @Override
    public void close() {
        applicationEntity.getDevice().unbindConnections();
    }

}
