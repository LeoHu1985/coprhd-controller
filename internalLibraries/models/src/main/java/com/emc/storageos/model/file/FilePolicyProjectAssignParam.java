/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.net.URI;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * @author jainm15
 */
public class FilePolicyProjectAssignParam implements Serializable {

    private static final long serialVersionUID = 1L;
    private boolean assigntoAll;
    private URI vpool;
    private Set<String> assignToProjects;

    @XmlElement(name = "vpool", required = true)
    public URI getVpool() {
        return this.vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }

    @XmlElement(name = "assign_to_all")
    public boolean isAssigntoAll() {
        return this.assigntoAll;
    }

    public void setAssigntoAll(boolean assigntoAll) {
        this.assigntoAll = assigntoAll;
    }

    @XmlElementWrapper(name = "assign_to_projects", required = true)
    @XmlElement(name = "project")
    public Set<String> getAssigntoProjects() {
        return this.assignToProjects;
    }

    public void setAssigntoProjects(Set<String> assignToProjects) {
        this.assignToProjects = assignToProjects;
    }

}
