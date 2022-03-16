package com.ReEncryptUtility.ReEncryptUtility.service;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DatabaseThreadContext.getCurrentDatabase();
    }

}
