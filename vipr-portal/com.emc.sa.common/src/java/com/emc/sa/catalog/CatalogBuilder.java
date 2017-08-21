/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.*;

import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.catalog.primitives.CustomServicesResourceDAOs;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.uimodels.*;
import com.emc.storageos.primitives.CustomServicesConstants;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptors;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.util.Messages;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.upgrade.callbacks.AllowRecurringSchedulerForApplicationServicesMigration;
import com.emc.storageos.db.client.upgrade.callbacks.AllowRecurringSchedulerMigration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.beans.factory.annotation.Autowired;

public class CatalogBuilder {
    private static final Logger log = Logger.getLogger(CatalogBuilder.class);
    private ModelClient models;
    private ServiceDescriptors descriptors;
    private WorkflowServiceDescriptor workflowServiceDescriptor;
    private CustomServicesPrimitiveDAOs daos;
    private CustomServicesResourceDAOs resourceDAOs;

    private Messages MESSAGES = new Messages(CatalogBuilder.class, "default-catalog");

    private int sortedIndexCounter = 1;

    public CatalogBuilder(ModelClient models, ServiceDescriptors descriptors, WorkflowServiceDescriptor workflowServiceDescriptor,
            CustomServicesPrimitiveDAOs daos, CustomServicesResourceDAOs resourceDAOs) {
        this.models = models;
        this.descriptors = descriptors;
        this.workflowServiceDescriptor = workflowServiceDescriptor;
        this.daos = daos;
        this.resourceDAOs = resourceDAOs;
    }

    public CatalogCategory buildCatalog(String tenant, URL resource) throws IOException {
        return buildCatalog(tenant, resource.openStream());
    }

    public CatalogCategory buildCatalog(String tenant, File f) throws IOException {
        return buildCatalog(tenant, new FileInputStream(f));
    }

    public CatalogCategory buildCatalog(String tenant, InputStream in) throws IOException {
        CategoryDef root = readCatalogDef(in);
        return saveCatalog(tenant, root);
    }

    public void clearCategory(CatalogCategory category) {
        models.delete(models.catalogServices().findByCatalogCategory(category.getId()));
        List<CatalogCategory> children = models.catalogCategories().findSubCatalogCategories(category.getId());
        for (CatalogCategory child : children) {
            clearCategory(child);
        }
        models.delete(category);
    }

    public static CategoryDef readCatalogDef(InputStream in) throws IOException {
        try {
            String catalog = IOUtils.toString(in);

            Gson gson = new GsonBuilder().create();
            CategoryDef root = gson.fromJson(catalog, CategoryDef.class);
            root.version = DigestUtils.sha1Hex(catalog);
            return root;
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    public static String getCatalogHash(InputStream in) throws IOException {
        return DigestUtils.sha1Hex(in);
    }

    protected CatalogCategory saveCatalog(String tenant, CategoryDef def) {
        NamedURI rootId = new NamedURI(URI.create(CatalogCategory.NO_PARENT), def.label);
        CatalogCategory cat = createCategory(tenant, def, rootId);
        log.info("Create Custom service Service");
        createCustomService(cat);

        return cat;
    }

    public CatalogCategory createCategory(String tenant, CategoryDef def, NamedURI parentId) {
        String label = getMessage(getLabel(def));
        String title = getMessage(def.title);
        String description = getMessage(def.description);

        CatalogCategory category = new CatalogCategory();
        category.setTenant(tenant);
        category.setLabel(StringUtils.deleteWhitespace(label));
        category.setTitle(title);
        category.setDescription(description);
        category.setImage(def.image);
        category.setCatalogCategoryId(parentId);
        category.setSortedIndex(sortedIndexCounter++);
        category.setVersion(def.version);
        models.save(category);

        NamedURI myId = new NamedURI(category.getId(), category.getLabel());
        if (def.categories != null) {
            for (CategoryDef categoryDef : def.categories) {
                createCategory(tenant, categoryDef, myId);
            }
        }
        if (def.services != null) {
            for (ServiceDef serviceDef : def.services) {
                createService(serviceDef, myId);
            }
        }

        return category;
    }

    public CatalogService createCustomService(CatalogCategory cat) {

        try {
            final File directory = new File(CustomServicesConstants.WORKFLOW_DIRECTORY);
            final File[] listOfFiles = directory.listFiles();
            log.info("get input stream");
            for (int i = 0; i < listOfFiles.length; i++) {
                final File file = listOfFiles[i];
                if (!(file.getName().endsWith(CustomServicesConstants.WORKFLOW_PACKAGE_EXT))) {
                    continue;
                }
                final InputStream in = new FileInputStream(file);

                final WFDirectory wfDirectory = new WFDirectory();
                log.info("call import of wf");
                CustomServicesWorkflow wf = WorkflowHelper.importWorkflow(in, wfDirectory, models, daos, resourceDAOs, true);

                log.info("call wf service descriptor");
                Collection<ServiceDescriptor> customDescriptors = workflowServiceDescriptor.listDescriptors();
                if (customDescriptors.isEmpty()) {
                    log.info("no cust service found");
                }
                for (ServiceDescriptor descriptor : customDescriptors) {
                    String label = descriptor.getTitle();
                    String title = descriptor.getTitle();
                    String description = descriptor.getDescription();

                    CatalogService service = new CatalogService();
                    service.setBaseService(wf.getId().toString());
                    service.setLabel(StringUtils.deleteWhitespace(label));
                    service.setTitle(title);
                    service.setDescription(description);
                    service.setImage("icon_aix.png");
                    log.info("cat image cat" + cat.getId() + cat.getLabel());
                    NamedURI myId = new NamedURI(cat.getId(), cat.getLabel());
                    service.setCatalogCategoryId(myId);
                    service.setSortedIndex(sortedIndexCounter++);
                    log.info("Create new custom service" + descriptor.getTitle() + descriptor.getDescription());
                    //TODO implement this
                    /*if (AllowRecurringSchedulerMigration.RECURRING_ALLOWED_CATALOG_SERVICES.contains(def.baseService)
                    || AllowRecurringSchedulerForApplicationServicesMigration.RECURRING_ALLOWED_CATALOG_SERVICES.contains(def.baseService)){
                    service.setRecurringAllowed(true);
                    }*/
                    models.save(service);

                    log.info("done creating service");
                    //TODO implement this
                    /*if (def.lockFields != null) {
                    for (Map.Entry<String, String> lockField : def.lockFields.entrySet()) {
                    CatalogServiceField field = new CatalogServiceField();
                    field.setLabel(lockField.getKey());
                    field.setValue(lockField.getValue());
                    field.setCatalogServiceId(new NamedURI(service.getId(), service.getLabel()));
                    models.save(field);
                    }
                    }*/
                }
            }
        } catch (Exception e) {
            log.info("exception " + e);
        }
        return null;
    }

    public CatalogService createService(ServiceDef def, NamedURI parentId) {
        ServiceDescriptor descriptor = descriptors.getDescriptor(Locale.getDefault(), def.baseService);
        String label = StringUtils.defaultString(getMessage(getLabel(def)), descriptor.getTitle());
        String title = StringUtils.defaultString(getMessage(def.title), descriptor.getTitle());
        String description = StringUtils.defaultString(getMessage(def.description), descriptor.getDescription());

        CatalogService service = new CatalogService();
        service.setBaseService(def.baseService);
        service.setLabel(StringUtils.deleteWhitespace(label));
        service.setTitle(title);
        service.setDescription(description);
        service.setImage(def.image);
        service.setCatalogCategoryId(parentId);
        service.setSortedIndex(sortedIndexCounter++);
        log.info("Create new service" + def.baseService);
        if (AllowRecurringSchedulerMigration.RECURRING_ALLOWED_CATALOG_SERVICES.contains(def.baseService) 
                || AllowRecurringSchedulerForApplicationServicesMigration.RECURRING_ALLOWED_CATALOG_SERVICES.contains(def.baseService)){
            service.setRecurringAllowed(true);
        }
        models.save(service);

        if (def.lockFields != null) {
            for (Map.Entry<String, String> lockField : def.lockFields.entrySet()) {
                CatalogServiceField field = new CatalogServiceField();
                field.setLabel(lockField.getKey());
                field.setValue(lockField.getValue());
                field.setCatalogServiceId(new NamedURI(service.getId(), service.getLabel()));
                models.save(field);
            }
        }

        return service;
    }

    protected String getLabel(CategoryDef def) {
        return StringUtils.defaultString(def.label, def.title);
    }

    protected String getLabel(ServiceDef def) {
        return StringUtils.defaultString(def.label, def.title);
    }

    protected String getMessage(String key) {
        try {
            return (key != null) ? MESSAGES.get(key) : null;
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
