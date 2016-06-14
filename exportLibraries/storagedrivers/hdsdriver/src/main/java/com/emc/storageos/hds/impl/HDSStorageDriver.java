package com.emc.storageos.hds.impl;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StorageHostComponent;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeMirror;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

public class HDSStorageDriver extends AbstractStorageDriver implements BlockStorageDriver {

    private static final Logger _log = LoggerFactory.getLogger(HDSStorageDriver.class);
    private HDSApiFactory hdsApiFactory = null;
    
    public HDSStorageDriver() {
        _log.info("Constructing HDSStorageDriver");
        if(hdsApiFactory == null) {
            hdsApiFactory = new HDSApiFactory();
            hdsApiFactory.init();
        }
    }
    
    @Override
    public RegistrationData getRegistrationData() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask getTask(String taskId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask discoverStorageHostComponents(StorageSystem storageSystem, List<StorageHostComponent> embeddedStorageHostComponents) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes, MutableInt token) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume volume) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<VolumeClone> getVolumeClones(StorageVolume volume) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<VolumeMirror> getVolumeMirrors(StorageVolume volume) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask expandVolume(StorageVolume volume, long newCapacity) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask stopManagement(StorageSystem storageSystem) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask deleteVolumes(List<StorageVolume> volumes) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask restoreSnapshot(List<VolumeSnapshot> snapshots) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask detachVolumeClone(List<VolumeClone> clones) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask restoreFromClone(List<VolumeClone> clones) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask deleteVolumeClone(List<VolumeClone> clones) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask createConsistencyGroupMirror(VolumeConsistencyGroup consistencyGroup, List<VolumeMirror> mirrors,
            List<CapabilityInstance> capabilities) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask deleteConsistencyGroupMirror(List<VolumeMirror> mirrors) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask restoreVolumeMirror(List<VolumeMirror> mirrors) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot snapshot) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone clone) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, HostExportInfo> getMirrorExportInfoForHosts(VolumeMirror mirror) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes, Map<String, String> volumeToHLUMap,
            List<StoragePort> recommendedPorts, List<StoragePort> availablePorts, StorageCapabilities capabilities,
            MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup, List<VolumeSnapshot> snapshots,
            List<CapabilityInstance> capabilities) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
            List<CapabilityInstance> capabilities) {
        // TODO Auto-generated method stub
        return null;
    }

}
