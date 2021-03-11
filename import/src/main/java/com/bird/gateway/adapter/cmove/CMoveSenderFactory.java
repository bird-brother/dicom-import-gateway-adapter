package com.bird.gateway.adapter.cmove;

import lombok.extern.slf4j.Slf4j;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.imaging.adapter.DeviceUtil;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;

/**
 * @date 14:50 2021-3-3
 * @description TODO
 */
@Slf4j
public class CMoveSenderFactory implements ISenderFactory{

    private final String cstoreSubAet;
    private final IGatewayClient gatewayClient;


    public CMoveSenderFactory(String cstoreSubAet, IGatewayClient gatewayClient) {
        this.cstoreSubAet = cstoreSubAet;
        this.gatewayClient = gatewayClient;
    }


    @Override
    public ISender create() {
        ApplicationEntity subApplicationEntity = new ApplicationEntity(cstoreSubAet);
        Connection conn = new Connection();
        DeviceUtil.createClientDevice(subApplicationEntity, conn);
        subApplicationEntity.addConnection(conn);

        return new CMoveSender(subApplicationEntity, gatewayClient);
    }


}
