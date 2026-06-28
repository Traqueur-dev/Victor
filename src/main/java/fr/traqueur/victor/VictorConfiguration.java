package fr.traqueur.victor;

import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.dialect.Dialect;

import java.util.Properties;
import java.util.Set;

public record VictorConfiguration(Dialect dialect, String connectionUrl, String username, String password,
                                  Properties connectionProperties, boolean showSql, boolean autoMigrate,
                                  Set<Class<? extends Entity<?>>> entityClasses) {

    public Properties connectionProperties() {
        return new Properties(connectionProperties);
    }
}