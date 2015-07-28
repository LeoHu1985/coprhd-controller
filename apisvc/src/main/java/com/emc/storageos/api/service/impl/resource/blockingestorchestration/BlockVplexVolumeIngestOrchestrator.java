/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
 /**  Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeExportIngestParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
/**
 * Responsible for ingesting vplex local and distributed virtual volumes.
 */
public class BlockVplexVolumeIngestOrchestrator extends BlockVolumeIngestOrchestrator {
    private static final Logger _logger = LoggerFactory.getLogger(BlockVplexVolumeIngestOrchestrator.class);

    private static final long CACHE_TIMEOUT = 600000; // ten minutes
    private long cacheLastRefreshed = 0;
    
    // maps the cluster id (1 or 2) to its name (e.g., cluster-1 or cluster-2)
    private Map<String, String> clusterIdToNameMap = new HashMap<String, String>();
    
    // maps each virtual array's URI to the cluster id (1 or 2) it connects to
    private Map<String, String> varrayToClusterIdMap = new HashMap<String, String>();
    
    // maps storage system URIs to StorageSystem objects
    private Map<String, StorageSystem> systemMap = new HashMap<String, StorageSystem>();
    
    private IngestStrategyFactory ingestStrategyFactory;
    
    public void setIngestStrategyFactory(IngestStrategyFactory ingestStrategyFactory) {
        this.ingestStrategyFactory = ingestStrategyFactory;
    }

    @Override
    public <T extends BlockObject> T ingestBlockObjects(List<URI> systemCache, List<URI> poolCache,StorageSystem system, UnManagedVolume unManagedVolume,
            VirtualPool vPool, VirtualArray virtualArray, Project project, TenantOrg tenant, List<UnManagedVolume> unManagedVolumesToBeDeleted, 
            Map<String, BlockObject> createdObjectMap, Map<String, List<DataObject>> updatedObjectMap, boolean unManagedVolumeExported, Class<T> clazz, 
            Map<String, StringBuffer> taskStatusMap) throws IngestionException {
        // For VPLEX volumes, verify that it is OK to ingest the unmanaged
        // volume into the requested virtual array.
        
        long timeRightNow = new Date().getTime();
        if (timeRightNow > (cacheLastRefreshed + CACHE_TIMEOUT)) {
            _logger.debug("clearing vplex ingestion api info cache");
            clusterIdToNameMap.clear();
            varrayToClusterIdMap.clear();
            cacheLastRefreshed = timeRightNow;
        }
        
        if (!VolumeIngestionUtil.isValidVarrayForUnmanagedVolume(unManagedVolume, virtualArray.getId(), 
                clusterIdToNameMap, varrayToClusterIdMap, _dbClient)) {
            _logger.warn("UnManaged Volume {} cannot be ingested into the requested varray. Skipping Ingestion.",
                    unManagedVolume.getLabel());
            
            throw IngestionException.exceptions.varrayIsInvalidForVplexVolume(virtualArray.getLabel(), unManagedVolume.getLabel());
        }
        
        try {
            List<URI> associatedVolumeUris = getAssociatedVolumes(unManagedVolume);
            
            if (null != associatedVolumeUris && !associatedVolumeUris.isEmpty()) {
                validateAssociatedVolumes(vPool, project, tenant, associatedVolumeUris);

                Map<String, UnManagedVolume> processedUnManagedVolumeMap = new HashMap<String, UnManagedVolume>();
                Map<String, BlockObject> vplexCreatedObjectMap = new HashMap<String, BlockObject>();
                Map<String, List<DataObject>> vplexUpdatedObjectMap = new HashMap<String, List<DataObject>>();
                
                for (URI associatedVolumeUri : associatedVolumeUris) {
                    UnManagedVolume associatedVolume = _dbClient.queryObject(UnManagedVolume.class,
                            associatedVolumeUri);
                    _logger.info("Ingestion started for exported vplex backend unmanagedvolume {}", associatedVolume.getNativeGuid());
                    
                    
                    // TODO how to track this as task(s)?
                    // String taskId = UUID.randomUUID().toString();
                    // Operation operation = _dbClient.createTaskOpStatus(UnManagedVolume.class, 
                    //        associatedVolumeUri, taskId, ResourceOperationTypeEnum.INGEST_EXPORTED_BLOCK_OBJECTS);  // TODO: new enum
                    
                    try {
                        
                        URI storageSystemUri = associatedVolume.getStorageSystemUri();
                        StorageSystem associatedSystem =  systemMap.get(storageSystemUri.toString());
                        if (null == associatedSystem) {
                            associatedSystem = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
                            systemMap.put(storageSystemUri.toString(), associatedSystem);
                        }
                        //Build the Strategy , which contains reference to Block object & export orchestrators
                        IngestStrategy ingestStrategy =  ingestStrategyFactory.buildIngestStrategy(associatedVolume);
                        
                        //TODO try to find ways to reduce parameters
                        @SuppressWarnings("unchecked")
                        BlockObject blockObject = ingestStrategy.ingestBlockObjects(systemCache, poolCache, 
                                associatedSystem, associatedVolume, vPool, virtualArray, 
                                project, tenant, unManagedVolumesToBeDeleted, vplexCreatedObjectMap, 
                                vplexUpdatedObjectMap, true, VolumeIngestionUtil.getBlockObjectClass(associatedVolume), taskStatusMap);
                        
                        _logger.info("Ingestion ended for exported unmanagedvolume {}", associatedVolume.getNativeGuid());
                        if (null == blockObject)  {
                            
                            // TODO: handle this by not ingesting any backend vols; leave a nice message in success response
                            continue;
//                            throw IngestionException.exceptions.generalVolumeException(
//                                    associatedVolume.getLabel(), "check the logs for more details");
                        }
                        
                        //TODO come up with a common response object to hold snaps/mirrors/clones
                        createdObjectMap.put(blockObject.getNativeGuid(), blockObject);
                        processedUnManagedVolumeMap.put(associatedVolume.getNativeGuid(), associatedVolume);
                    } catch ( APIException ex ) {
                        _logger.warn(ex.getLocalizedMessage(), ex);
//                        _dbClient.error(UnManagedVolume.class, associatedVolumeUri, taskId, ex);
                    } catch ( Exception ex ) {
                        _logger.warn(ex.getLocalizedMessage(), ex);
//                        _dbClient.error(UnManagedVolume.class, associatedVolumeUri, 
//                                taskId, IngestionException.exceptions.generalVolumeException(
//                                        associatedVolume.getLabel(), ex.getLocalizedMessage()));
                    }
                    
//                    TaskResourceRep task = toTask(associatedVolume, taskId, operation);
//                    taskMap.put(associatedVolume.getId().toString(), task);
                }
                
                
                
                
                
                
                // TODO figure out how to create export group
                boolean exportGroupCreated = false;
                ExportGroup exportGroup = null; 

                List<BlockObject> ingestedObjects = new ArrayList<BlockObject>();
                
                for(String unManagedVolumeGUID: processedUnManagedVolumeMap.keySet()) {
                    String objectGUID = unManagedVolumeGUID.replace(VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
                    BlockObject processedBlockObject = vplexCreatedObjectMap.get(objectGUID);
                    UnManagedVolume processedUnManagedVolume = processedUnManagedVolumeMap.get(unManagedVolumeGUID);
//                    URI unManagedVolumeUri = processedUnManagedVolume.getId();
//                    String taskId = taskMap.get(processedUnManagedVolume.getId().toString()).getOpId();
                    try {
                        if(processedBlockObject == null) {
                            _logger.warn("The ingested block object is null. Skipping ingestion of export masks for unmanaged volume {}", unManagedVolumeGUID);
                            continue;
//                            throw IngestionException.exceptions.generalVolumeException(
//                                    processedUnManagedVolume.getLabel(), "check the logs for more details");
                        }
                        
                        URI storageSystemUri = processedUnManagedVolume.getStorageSystemUri();
                        StorageSystem associatedSystem =  systemMap.get(storageSystemUri.toString());
                        //Build the Strategy , which contains reference to Block object & export orchestrators
                        IngestExportStrategy ingestStrategy =  ingestStrategyFactory.buildIngestExportStrategy(processedUnManagedVolume);
                        
                        VolumeExportIngestParam exportIngestParam = new VolumeExportIngestParam();
                        exportIngestParam.setProject(project.getId());
                        exportIngestParam.setUnManagedVolumes(associatedVolumeUris);
                        exportIngestParam.setVarray(virtualArray.getId());
                        exportIngestParam.setVpool(vPool.getId());
                        
                        // TODO: figure out how to set VPLEX as host
                        // exportIngestParam.setHost(host);
                        
                        BlockObject blockObject = ingestStrategy.ingestExportMasks(processedUnManagedVolume, exportIngestParam, exportGroup, 
                                processedBlockObject, unManagedVolumesToBeDeleted, associatedSystem, exportGroupCreated);
                        if (null == blockObject)  {
                            
                            _logger.warn("blockObject was null");
                            continue;
//                            throw IngestionException.exceptions.generalVolumeException(
//                                    processedUnManagedVolume.getLabel(), "check the logs for more details");
                        }
                        ingestedObjects.add(blockObject);
                        if(blockObject.checkInternalFlags(Flag.NO_PUBLIC_ACCESS)) {
                            StringBuffer taskStatus = taskStatusMap.get(processedUnManagedVolume.getNativeGuid());
                            String taskMessage = "";
                            if(taskStatus == null) {
                                //No task status found. Put in a default message.
                                taskMessage = String.format("Not all the parent/replicas of unManagedVolume %s have been ingested", processedUnManagedVolume.getLabel());
                            } else {
                                taskMessage = taskStatus.toString();
                            }
                            _logger.error(taskMessage);
//                            _dbClient.error(UnManagedVolume.class, processedUnManagedVolume.getId(), taskId, 
//                                    IngestionException.exceptions.unmanagedVolumeIsNotVisible(processedUnManagedVolume.getLabel(), taskMessage));
                        } else {
//                            _dbClient.ready(UnManagedVolume.class, 
//                                    processedUnManagedVolume.getId(), taskId, "Successfully ingested exported volume and its masks."); // TODO: convert to props message
                        }
                        //Update the related objects if any after successful export mask ingestion
                        List<DataObject> updatedObjects = updatedObjectMap.get(unManagedVolumeGUID);
                        if(updatedObjects != null && !updatedObjects.isEmpty()) {
                            _dbClient.updateAndReindexObject(updatedObjects);
                        }
                        
                    } catch ( APIException ex ) {
                        _logger.warn(ex.getLocalizedMessage(), ex);
//                        _dbClient.error(UnManagedVolume.class, unManagedVolumeUri, taskId, ex);
                    } catch ( Exception ex ) {
                        _logger.warn(ex.getLocalizedMessage(), ex);
//                        _dbClient.error(UnManagedVolume.class, unManagedVolumeUri, 
//                                taskId, IngestionException.exceptions.generalVolumeException(
//                                        processedUnManagedVolume.getLabel(), ex.getLocalizedMessage()));
                    }
                }
                
                
                _dbClient.createObject(ingestedObjects);
                _dbClient.persistObject(processedUnManagedVolumeMap.values());
                // record the events after they have been persisted
//                for (BlockObject volume : ingestedObjects) {
//                    recordVolumeOperation(_dbClient, getOpByBlockObjectType(volume),
//                            Status.ready, volume.getId());
//                }

                
                
            }
        } catch (Exception ex) {
            
            // TODO: error handlin'
            _logger.error("error!!!", ex);
        }
        
        // TODO obviously remove this
        boolean tickles = true;
        if (tickles) {
            throw IngestionException.exceptions.generalException("throwing exception to stop ingestion during testing!!!!!");
        }
        
        return super.ingestBlockObjects(systemCache, poolCache, system, unManagedVolume, vPool, virtualArray, project, tenant,
                unManagedVolumesToBeDeleted, createdObjectMap, updatedObjectMap, unManagedVolumeExported, clazz, taskStatusMap);
    }

    private List<URI> getAssociatedVolumes(UnManagedVolume unManagedVolume) {
        String deviceName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_SUPPORTING_DEVICE_NAME.toString(),
                    unManagedVolume.getVolumeInformation());
        
        String locality = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_LOCALITY.toString(),
                    unManagedVolume.getVolumeInformation());
        
        Map<String, String> backendVolumeMap = 
                VPlexControllerUtils.getStorageVolumeInfoForDevice(
                        deviceName, locality, 
                        unManagedVolume.getStorageSystemUri(), _dbClient);
        
        List<URI> associatedVolumes = new ArrayList<URI>();
        
        for (Entry<String, String> entry : backendVolumeMap.entrySet()) {
            _logger.info("attempting to find unmanaged backend volume {} with wwn {}", 
                    entry.getKey(), entry.getValue());
            
            String backendWwn = entry.getValue();
            URIQueryResultList results = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.
                    Factory.getUnmanagedVolumeWwnConstraint(
                            BlockObject.normalizeWWN(backendWwn)), results);
            if (results.iterator() != null) {
                for (URI uri : results) {
                    associatedVolumes.add(uri);
                }
            }
        }
        
        _logger.info("for VPLEX UnManagedVolume {} found these associated volumes: " + associatedVolumes, unManagedVolume.getId());
        return associatedVolumes;
    }

    private void validateAssociatedVolumes(VirtualPool vPool, Project project,
            TenantOrg tenant, List<URI> associatedVolumes) throws Exception {
        // validation
        // check if selected vpool can contain all the backend volumes
        // check quotas
        
        
        
        // check for Quotas
        long unManagedVolumesCapacity = VolumeIngestionUtil.getTotalUnManagedVolumeCapacity(_dbClient, associatedVolumes);
        _logger.info("UnManagedVolume provisioning quota validation successful");
        CapacityUtils.validateQuotasForProvisioning(_dbClient, vPool, project, tenant, unManagedVolumesCapacity, "volume");
        VolumeIngestionUtil.checkIngestionRequestValidForUnManagedVolumes(associatedVolumes, vPool, _dbClient);
    }
    
    @Override
    protected void updateBlockObjectNativeIds(BlockObject blockObject, UnManagedVolume unManagedVolume) {
        String label = unManagedVolume.getLabel();
        blockObject.setDeviceLabel(label);
        blockObject.setLabel(label);
        blockObject.setNativeId(blockObject.getNativeGuid());
    }

    @Override
    protected URI getConsistencyGroupUri(UnManagedVolume unManagedVolume, VirtualPool vPool, URI project, URI tenant,
            URI virtualArray, DbClient dbClient) {
        return VolumeIngestionUtil.getVplexConsistencyGroup(unManagedVolume, vPool, project, tenant, virtualArray, dbClient);
    }

    @Override
    protected void updateCGPropertiesInVolume(URI consistencyGroupUri, Volume volume, StorageSystem system,
            UnManagedVolume unManagedVolume) {
        if (consistencyGroupUri != null) {

            String cgName = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.VPLEX_CONSISTENCY_GROUP_NAME.toString(), unManagedVolume.getVolumeInformation());

            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupUri);

            StringSet unmanagedVolumeClusters = unManagedVolume.getVolumeInformation().get(
                    SupportedVolumeInformation.VPLEX_CLUSTER_IDS.toString());
            // Add a ViPR CG mapping for each of the VPlex clusters the VPlex CG
            // belongs to.
            if (unmanagedVolumeClusters != null && !unmanagedVolumeClusters.isEmpty()) {
                Iterator<String> unmanagedVolumeClustersItr = unmanagedVolumeClusters.iterator();
                while (unmanagedVolumeClustersItr.hasNext()) {
                    cg.addSystemConsistencyGroup(system.getId().toString(),
                            BlockConsistencyGroupUtils.buildClusterCgName(unmanagedVolumeClustersItr.next(), cgName));
                }

                _dbClient.updateAndReindexObject(cg);
            }

            volume.setConsistencyGroup(consistencyGroupUri);
        }
    }

    @Override
    protected void setProtocol(StoragePool pool, Volume volume, VirtualPool vPool) {
        if (null == volume.getProtocol()) {
            volume.setProtocol(new StringSet());
        }
        volume.getProtocol().addAll(vPool.getProtocols());
    }

    @Override
    protected StoragePool validateAndReturnStoragePoolInVAarray(UnManagedVolume unManagedVolume, VirtualArray virtualArray) {
        return null;
    }

    @Override
    protected void checkSystemResourceLimitsExceeded(StorageSystem system, UnManagedVolume unManagedVolume, List<URI> systemCache) {
        //always return true,as for vplex volumes this limit doesn't have any effect
        return;
    }

    @Override
    protected void checkPoolResourceLimitsExceeded(StorageSystem system, StoragePool pool, UnManagedVolume unManagedVolume,
            List<URI> poolCache) {
        //always return true, as pool will be null
        return;
    }

    @Override
    protected void checkUnManagedVolumeAddedToCG(UnManagedVolume unManagedVolume, VirtualArray virtualArray, TenantOrg tenant,
            Project project, VirtualPool vPool) {
        if (VolumeIngestionUtil.checkUnManagedResourceAddedToConsistencyGroup(unManagedVolume)) {
            URI consistencyGroupUri = VolumeIngestionUtil.getVplexConsistencyGroup(unManagedVolume, vPool, project.getId(),
                    tenant.getId(), virtualArray.getId(), _dbClient);
            if (null == consistencyGroupUri) {
                _logger.warn("A Consistency Group for the VPLEX volume could not be determined. Skipping Ingestion.");
                throw IngestionException.exceptions.unmanagedVolumeVplexConsistencyGroupCouldNotBeIdentified(unManagedVolume.getLabel());
            }

        }
    }
	
    @Override
    protected void validateAutoTierPolicy(String autoTierPolicyId, UnManagedVolume unManagedVolume, VirtualPool vPool) {
        super.validateAutoTierPolicy(autoTierPolicyId, unManagedVolume, vPool);
    }
}
