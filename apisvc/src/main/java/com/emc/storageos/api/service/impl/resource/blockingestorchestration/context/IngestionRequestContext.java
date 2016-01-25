/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 * IngestionRequestContext is instantiated once per user request
 * for ingestion of UnManagedVolumes in the UnManagedVolume service.
 * It can be used for ingestion of both exported and unexported volumes.
 * 
 * It can also be used by Volume types that have a "backend" concept,
 * such as VPLEX or RecoverPoint volumes, to encapsulate everything
 * dependent that must be ingested for that volume. For example,
 * VplexVolumeIngestionContext, a VolumeIngestionContext, also implements
 * this interface, allowing it to be used as a nested IngestionRequestContext
 * for its backend volumes and exports.
 * 
 * This class implements Iterator<UnManagedVolume> and holds a nested
 * iterator of URIs for these UnManagedVolumes, so the UnManagedVolumeService
 * can iterate this class directly. Each UnManagedVolume is
 * instantiated when next is called, and this ensure the current unmanaged
 * volume is set correctly and the current VolumeIngestionContext is
 * created for the currently iterating volume.
 * 
 * This class includes a VolumeIngestionContextFactory that will
 * creates the correct VolumeIngestionContext object for the current
 * volume based on the UnManagedVolume type.
 * 
 * This class holds all the tracking collections for persistence of
 * ingested unmanaged objects at the end of a successful ingestion run.
 */
public interface IngestionRequestContext extends Iterator<UnManagedVolume> {

    /**
     * Returns the UnManagedVolume currently being processed by ingestion.
     * 
     * @return the UnManagedVolume currently being processed
     */
    public UnManagedVolume getCurrentUnmanagedVolume();

    /**
     * Returns the UnManagedVolume URI currently being processed by ingestion.
     * 
     * @return the UnManagedVolume URI currently being processed
     */
    public URI getCurrentUnManagedVolumeUri();

    /**
     * Returns the VolumeIngestionContext currently being processed by ingestion.
     * 
     * @return the VolumeIngestionContext currently being processed
     */
    public VolumeIngestionContext getVolumeContext();

    /**
     * Finds and returns a VolumeIngestionContext for the given UnManagedVolume
     * nativeGuid, or null if none was found.
     * 
     * @param unmanagedVolumeGuid the UnManagedVolume nativeGuid to check
     * @return a VolumeIngestionContext for the UnManagedVolume nativeGuid
     */
    public VolumeIngestionContext getVolumeContext(String unmanagedVolumeGuid);

    /**
     * Returns the StorageSystem for the UnManagedVolume currently being processed.
     * 
     * @return the StorageSystem for the UnManagedVolume currently being processed
     */
    public StorageSystem getStorageSystem();

    /**
     * Returns the VirtualPool for the UnManagedVolume currently being processed.
     * 
     * @return the VirtualPool for the UnManagedVolume currently being processed
     */
    public VirtualPool getVpool();

    /**
     * Returns the VirtualArray for the UnManagedVolume currently being processed.
     * 
     * @return the VirtualArray for the UnManagedVolume currently being processed
     */
    public VirtualArray getVarray();

    /**
     * Returns the Project for the UnManagedVolume currently being processed.
     * 
     * @return the Project for the UnManagedVolume currently being processed
     */
    public Project getProject();

    /**
     * Returns the TenantOrg for the UnManagedVolume currently being processed.
     * 
     * @return the TenantOrg for the UnManagedVolume currently being processed
     */
    public TenantOrg getTenant();

    /**
     * Returns the VPLEX ingestion method for all the UnManagedVolumes currently
     * being processed in this ingestion request.
     * 
     * @return the VPLEX ingestion method String
     */
    public String getVplexIngestionMethod();

    /**
     * Returns a cache of loaded StorageSystems mapped to their URI Strings.
     * 
     * @return a cache Map of StorageSystem URI String to StorageSystem Objects
     */
    public Map<String, StorageSystem> getStorageSystemCache();

    /**
     * Returns a List of URIs for StorageSystems whose capacity limits have been
     * exceeded before or during this ingestion request.
     * 
     * @return a List of StorageSystem URIs
     */
    public List<URI> getExhaustedStorageSystems();

    /**
     * Returns a List of URIs for StoragePools whose capacity limits have been
     * exceeded before or during this ingestion request.
     * 
     * @return a List of StoragePool URIs
     */
    public List<URI> getExhaustedPools();

    /**
     * Returns a List of UnManagedVolumes that have been
     * successfully processed and can be marked for deletion
     * at the end of this whole ingestion request.
     * 
     * @return a List of UnManagedVolumes
     */
    public List<UnManagedVolume> getUnManagedVolumesToBeDeleted();

    /**
     * Returns a Map of BlockObjects created by ingestion
     * as mapped by their native GUID for the key.
     * 
     * @return a Map of native GUID Strings to BlockObjects
     */
    public Map<String, BlockObject> getObjectsToBeCreatedMap();

    /**
     * Returns a Map of a List of DataObjects updated by ingestion
     * as mapped to the native GUID of the UnManagedVolume Object
     * for which they were updated.
     * 
     * @return a Map of UnManagedVolume native GUID Strings to a
     *         List of associated updated DataObjects
     */
    public Map<String, List<DataObject>> getObjectsToBeUpdatedMap();

    /**
     * Returns a Map of UnManagedVolume native GUID Strings to
     * StringBuffer for its task status to be returned in the
     * response to this ingestion request.
     * 
     * @return a Map of UnManagedVolume native GUID Strings to
     *         task status StringBuffers
     */
    public Map<String, StringBuffer> getTaskStatusMap();

    /**
     * Returns a Map of UnManagedVolume native GUID Strings to
     * its associated VolumeIngestionContext.
     * 
     * @return a Map of UnManagedVolume native GUID Strings to
     *         VolumeIngestionContext objects
     */
    public Map<String, VolumeIngestionContext> getProcessedUnManagedVolumeMap();

    /**
     * Returns a List of UnManagedConsistecyGroups to update.
     * 
     * @return a List of UnManagedConsistencyGroup objects to update.
     */
    public List<UnManagedConsistencyGroup> getUnManagedCGsToUpdate();

    /**
     * Returns the UnManagedVolume that has been processed for the given nativeGuid,
     * or null if none was found.
     * 
     * @param nativeGuid the UnManagedVolume to check
     * @return an UnManagedVolume
     */
    public UnManagedVolume getProcessedUnManagedVolume(String nativeGuid);

    /**
     * Returns the BlockObject that has been processed for the given nativeGuid,
     * or null if none was found.
     * 
     * @param nativeGuid the BlockObject to check
     * @return a BlockObject
     */
    public BlockObject getProcessedBlockObject(String unmanagedVolumeGuid);

    /**
     * Returns the VolumeIngestionContext for the given nativeGuid,
     * or null if none was found in the processed UnManagedVolume Map.
     * 
     * @param nativeGuid the UnManagedVolume to check
     * @return a VolumeIngestionContext
     */
    public VolumeIngestionContext getProcessedVolumeContext(String nativeGuid);

    /**
     * Returns the error messages collection for the given nativeGuid,
     * or an empty List of Strings if none was found.
     * 
     * @param nativeGuid the UnManagedVolume to check
     * @return a List of error messages for the given UnManagedVolume native GUID
     */
    public List<String> getErrorMessagesForVolume(String nativeGuid);

    /**
     * Returns a List of BlockObjects that were completely ingested at
     * the end of Export Mask ingestion.
     * 
     * @return a List of BlockObjects ingested after export processing
     */
    public List<BlockObject> getObjectsIngestedByExportProcessing();

    /**
     * Returns true if the ExportGroup in this IngestionRequestContext
     * was created by it, rather than being fetched from the database
     * as an already existing ExportGroup.
     * 
     * @return true if the ExportGroup was created during this ingestion request
     */
    public boolean isExportGroupCreated();

    /**
     * Sets the status of ExportGroupCreated, which represents whether
     * the ExportGroup in this IngestionRequestContext
     * was created by it, rather than being fetched from the database
     * as an already existing ExportGroup.
     * 
     * @param exportGroupCreated boolean representing the ExportGroup creation status
     */
    public void setExportGroupCreated(boolean exportGroupCreated);

    /**
     * Returns the ExportGroup for this ingestion request.
     * 
     * @return the ExportGroup for this ingestion request
     */
    public ExportGroup getExportGroup();

    /**
     * Sets the ExportGroup for this ingestion request.
     * 
     * @param exportGroup the ExportGroup to set for this ingestion request
     */
    public void setExportGroup(ExportGroup exportGroup);

    /**
     * Returns the Host URI for this ingestion request.
     * 
     * @return the Host URI for this ingestion request
     */
    public URI getHost();

    /**
     * Sets the Host URI for this ingestion request
     * 
     * @param host the Host URI for this ingestion request
     */
    public void setHost(URI host);

    /**
     * Returns the Cluster URI for this ingestion request.
     * 
     * @return the Cluster URI for this ingestion request
     */
    public URI getCluster();

    /**
     * Sets the Cluster URI for this ingestion request
     * 
     * @param cluster the Cluster URI for this ingestion request
     */
    public void setCluster(URI cluster);

    /**
     * Returns a List of Initiator Objects for this ingestion request.
     * 
     * @return the List of Initiator objects for this ingestion request
     */
    public List<Initiator> getDeviceInitiators();

    /**
     * Sets the List of Initiator Objects for this ingestion request
     * 
     * @param deviceInitiators the List of Initiator objects for this ingestion request
     */
    public void setDeviceInitiators(List<Initiator> deviceInitiators);

    /**
     * Finds a BlockObject by native GUID by first looking in the database,
     * then in the context's createdObjectMap (or parent contexts, if needed).
     * 
     * @param nativeGuid the BlockObject native GUID to look for
     * @return a BlockObject for the native GUID on null if none found
     */
    public BlockObject findCreatedBlockObject(String nativeGuid);

}