package com.bird.gateway.adapter.cstore.destination;

import java.io.IOException;
import java.io.InputStream;


/**
 * @date 14:54 2021-3-2
 * @description TODO
 */
public interface IDestinationClientFactory {

    DestinationHolder create(String callingAet, InputStream inPdvStream) throws IOException;

}
