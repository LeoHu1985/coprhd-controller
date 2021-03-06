/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import com.emc.storageos.management.backup.ExternalServerType;

import java.io.OutputStream;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.List;

/**
 * This class uploads backups to user supplied external file server.
 */
public abstract class Uploader {
    private BackupScheduler cli;
    protected SchedulerConfig cfg;

    public static Uploader create(SchedulerConfig cfg, BackupScheduler cli) throws Exception {
        cfg.reload();
        if (cfg.uploadUrl == null) {
            return null;
        }

        if (ExternalServerType.FTP == cfg.getUploadServerType() && FtpsUploader.isSupported(cfg.uploadUrl) )
            return new FtpsUploader(cfg, cli);

        if (ExternalServerType.CIFS == cfg.getUploadServerType() && CifsUploader.isSupported(cfg.uploadUrl)) {
            return new CifsUploader(cfg,cli);
        }
        throw new UnsupportedAddressTypeException();
    }

    protected Uploader(SchedulerConfig cfg, BackupScheduler cli) {
        this.cfg = cfg;
        this.cli = cli;
    }

    /**
     * Get size of a file on server.
     *
     * @param fileName the name of the file for which to get size info.
     * @return file size in bytes, or null if file is not exist.
     * @throws Exception
     */
    public abstract Long getFileSize(String fileName) throws Exception;

    /**
     * Upload file with resuming.
     *
     * @param fileName the file on server to be uploaded to.
     * @param offset from which offset on server to resume upload.
     * @return The OutputStream instance to which upload data can be written.
     * @throws Exception
     */
    public abstract OutputStream upload(String fileName, long offset) throws Exception;

    /**
     * List files with specific prefix on server.
     *
     * @param prefix files with the prefix to be listed.
     * @return List contain file names.
     * @throws Exception
     */
    public abstract List<String> listFiles(String prefix) throws Exception;

    /**
     * Rename specific file on server.
     *
     * @param sourceFileName to be renamed filename on server
     * @param destFileName   rename to filename on server
     * @throws Exception
     */
    public abstract void rename(String sourceFileName, String destFileName) throws Exception;
}
