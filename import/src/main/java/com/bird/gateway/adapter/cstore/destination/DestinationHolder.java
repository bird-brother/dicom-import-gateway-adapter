package com.bird.gateway.adapter.cstore.destination;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CountingInputStream;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.imaging.adapter.AetDictionary.*;

import java.io.InputStream;

/**
 * @date 14:54 2021-3-2
 * @description TODO
 */
public class DestinationHolder {

    private IGatewayClient singleDestination;
    private ImmutableList<IGatewayClient> healthcareDestinations;
    private ImmutableList<Aet> dicomDestinations;
    private CountingInputStream countingInputStream;


    public DestinationHolder(InputStream destinationInputStream, IGatewayClient defaultDestination) {
        this.countingInputStream = new CountingInputStream(destinationInputStream);
        //default values
        this.singleDestination = defaultDestination;
        this.healthcareDestinations = ImmutableList.of(defaultDestination);
        this.dicomDestinations = ImmutableList.of();
    }


    public CountingInputStream getCountingInputStream() {
        return countingInputStream;
    }


    public void setSingleDestination(IGatewayClient dicomWebClient) {
        this.singleDestination = dicomWebClient;
    }


    public IGatewayClient getSingleDestination() {
        return singleDestination;
    }


    public void setHealthcareDestinations(ImmutableList<IGatewayClient> healthcareDestinations) {
        this.healthcareDestinations = healthcareDestinations;
    }

    public void setDicomDestinations(ImmutableList<Aet> dicomDestinations) {
        this.dicomDestinations = dicomDestinations;
    }

    public ImmutableList<IGatewayClient> getHealthcareDestinations() {
        return healthcareDestinations;
    }

    public ImmutableList<Aet> getDicomDestinations() {
        return dicomDestinations;
    }


}
