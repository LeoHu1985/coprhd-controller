/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.db.client.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.sun.tools.internal.xjc.reader.Ring;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by wangs12 on 8/2/2017.
 */
public class DbViewMetaRecord {
    private final DbViewDefinition viewDef;
    private String keyName;
    private String keyValue;
    private UUID timeUUID;
    private String tableName;

    private List<ViewColumn> columns = new ArrayList<>();

    public DbViewDefinition getViewDef() {
        return viewDef;
    }

    public DbViewMetaRecord(DbViewDefinition viewDef) {
        this.viewDef = viewDef;
        this.tableName = viewDef.getMetaViewName();
        this.keyName = viewDef.getClusterColumnNames().get(viewDef.getClusterColumnNames().size()-1);
    }

    public void setKeyValue(String key) {
        this.keyValue = key;
    }

    public void addColumn(ViewColumn col) {
        columns.add(col);
    }

    public List<ViewColumn> getColumns() {
        return columns;
    }

    public String getKeyValue() {
        return keyValue;
    }

    public UUID getTimeUUID() {
        return timeUUID;
    }

    public void setTimeUUID(UUID timeUUID) {
        this.timeUUID = timeUUID;
    }

    public static DbViewMetaRecord build(DbViewRecord view) {
        DbViewMetaRecord viewMetaRecord = new DbViewMetaRecord(view.getViewDef());
        viewMetaRecord.tableName = view.getViewDef().getMetaViewName();

        ViewColumn lastCol = view.getClusterColumns().get(view.getClusterColumns().size()-1);
        viewMetaRecord.keyValue = (String) lastCol.getValue();
        viewMetaRecord.keyName = lastCol.getName();

        // take key of view as first column in meta
        viewMetaRecord.addColumn(new ViewColumn(view.getKeyName(), view.getKeyValue(), view.getKeyValue().getClass()));
        for (int i = 0; i < view.getClusterColumns().size()-1; i++) {
            viewMetaRecord.addColumn(view.getClusterColumns().get(i));
        }

        viewMetaRecord.timeUUID = view.getTimeUUID();
        return viewMetaRecord;
    }

    @Override
    public String toString() {
        return "DbViewMetaRecord{" +
                "keyName='" + keyName + '\'' +
                ", keyValue='" + keyValue + '\'' +
                ", timeUUID=" + timeUUID +
                ", tableName='" + tableName + '\'' +
                ", columns=" + columns +
                '}';
    }

    public String getKeyName() {
        return keyName;
    }

    public String getUpsertCql() {
        StringBuilder cql = new StringBuilder();
        cql.append("UPDATE " + this.tableName + " SET ");

        for (ViewColumn col: columns) {
            cql.append(col.getName() + " = ?,");
        }
        cql.deleteCharAt(cql.length()-1);

        cql.append(" WHERE " + keyName + " = ? and timeuuid = ?");
        return cql.toString();
    }

    public String getDeleteCql() {
        String cql = "DELETE FROM " + this.tableName + " WHERE " + keyName + " = ? AND timeuuid = ?";
        return cql;
    }
}