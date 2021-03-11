package com.bird.gateway.adapter.cstore.multipledest.sender;

import org.bird.gateway.imaging.adapter.DeviceUtil;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;

/**
 * @date 14:59 2021-3-2
 * @description TODO
 */
public class CStoreSenderFactory {

    private final String cstoreSubAet;

    public CStoreSenderFactory(String cstoreSubAet) {
        this.cstoreSubAet = cstoreSubAet;
    }

    public CStoreSender create() {
        ApplicationEntity subApplicationEntity = new ApplicationEntity(cstoreSubAet);
        Connection conn = new Connection();
        DeviceUtil.createClientDevice(subApplicationEntity, conn);
        subApplicationEntity.addConnection(conn);

        return new CStoreSender(subApplicationEntity);
    }


}
