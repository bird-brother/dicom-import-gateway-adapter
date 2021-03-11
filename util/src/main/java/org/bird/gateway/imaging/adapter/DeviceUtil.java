package org.bird.gateway.imaging.adapter;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.service.DicomServiceRegistry;

import java.util.concurrent.Executors;

/**
 * @date 10:42 2021-3-1
 * @description Static utilities for creating {@link Device} used in dcm4che library.
 */
public class DeviceUtil {


    private static final String ALL_ALLOWED_SOP_CLASSES = "*";
    private static final String ALL_ALLOWED_TRANSFER_SYNTAXES = "*";
    private DeviceUtil() {}


    /**
     * Creates a DICOM server listening to the port for the given services handling all syntaxes
     * @params
     * @return Device
     * @since 2021-3-1 10:50
     */
    public static Device createServerDevice(String applicationEntityName, Integer dicomPort, DicomServiceRegistry serviceRegistry){
        /* commonName */
        TransferCapability transferCapability = new TransferCapability(null, ALL_ALLOWED_SOP_CLASSES,
                TransferCapability.Role.SCP, ALL_ALLOWED_TRANSFER_SYNTAXES);
        return createServerDevice(applicationEntityName, dicomPort, serviceRegistry, transferCapability);
    }

    /**
     * Creates a DICOM server listening to the port for the given services
     * @params
     * @return Device
     * @since 2021-3-1 10:56
     */
    public static Device createServerDevice(String applicationEntityName, Integer dicomPort,
                                            DicomServiceRegistry serviceRegistry, TransferCapability transferCapability) {

        // Create a DICOM device.
        Device device = new Device("dicom-to-dicomweb-adapter-server");
        Connection connection = new Connection();
        connection.setPort(dicomPort);
        device.addConnection(connection);


        // Create an application entity (a network node) listening on input port.
        ApplicationEntity applicationEntity = new ApplicationEntity(applicationEntityName);
        applicationEntity.setAssociationAcceptor(true);
        applicationEntity.addConnection(connection);
        applicationEntity.addTransferCapability(transferCapability);
        device.addApplicationEntity(applicationEntity);

        // Add the DICOM request handlers to the device.
        device.setDimseRQHandler(serviceRegistry);
        device.setScheduledExecutor(Executors.newSingleThreadScheduledExecutor());
        device.setExecutor(Executors.newCachedThreadPool());
        return device;
    }


    /**
     * Creates a DICOM client device containing the given Application Entity
     * @params
     * @return Device
     * @since 2021-3-1 10:56
     */
    public static Device createClientDevice(
            ApplicationEntity applicationEntity, Connection connection) {
        Device device = new Device("dicom-to-dicomweb-adapter-client");
        device.addConnection(connection);
        device.addApplicationEntity(applicationEntity);
        device.setScheduledExecutor(Executors.newSingleThreadScheduledExecutor());
        device.setExecutor(Executors.newCachedThreadPool());
        return device;
    }











}
