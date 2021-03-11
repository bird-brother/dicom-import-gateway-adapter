package com.bird.gateway.adapter.cmove;

import org.bird.gateway.IGatewayClient.*;
import org.bird.gateway.imaging.adapter.AetDictionary.*;

import java.io.Closeable;
import java.io.IOException;

/**
 * @date 14:50 2021-3-3
 * @description TODO
 */
public interface ISender extends Closeable {

    /**
     * Sends instance via c-store (or test stub) to target AET, returns bytes sent
     */
    long cmove(
            Aet target,
            String studyUid,
            String seriesUid,
            String sopInstanceUid,
            String sopClassUid)
            throws DicomGatewayException, IOException, InterruptedException;


}
