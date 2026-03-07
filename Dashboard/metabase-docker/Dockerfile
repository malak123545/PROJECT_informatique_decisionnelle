# ============================================================
# Metabase + Oracle JDBC driver (ojdbc8 téléchargé depuis Maven)
# ============================================================
FROM metabase/metabase:v0.50.20

# Téléchargement automatique du driver Oracle JDBC depuis Maven Central
RUN curl -L \
    "https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc8/21.9.0.0/ojdbc8-21.9.0.0.jar" \
    -o /plugins/ojdbc8.jar

