package fr.traqueur.victor;

import fr.traqueur.victor.types.VictorDialect;

import java.util.Properties;
import java.util.Set;

public record VictorConfiguration(VictorDialect dialect, String connectionUrl, String username, String password,
                                  Properties connectionProperties, boolean showSql, boolean autoMigrate,
                                  Set<Class<?>> entityClasses) {

    public Properties connectionProperties() {
        return new Properties(connectionProperties);
    }
}