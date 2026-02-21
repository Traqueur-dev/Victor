package fr.traqueur.victor;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.dialect.Dialect;

import java.util.Properties;
import java.util.Set;

public record VictorConfiguration(Dialect dialect, String connectionUrl, String username, String password,
                                  Properties connectionProperties, boolean showSql, boolean autoMigrate,
                                  Set<Class<? extends Dto<?>>> dtoClasses) {

    public Properties connectionProperties() {
        return new Properties(connectionProperties);
    }
}