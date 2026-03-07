from Data_base_connexion import connect, disconnect, create_schema, drop_schema,list_schemas, list_tables, list_columns

conn = connect()

# Etape 1 : Supprimer l'ancien schema
drop_schema(conn)

# Etape 2 : Recreer le schema avec le nouveau fichier
create_schema(conn, "datawarehouse_yelp.sql")

# Etape 3 : Verifier les colonnes APRES creation
cursor = conn.cursor()
cursor.execute("SELECT table_name FROM user_tables ORDER BY table_name")
tables = [row[0] for row in cursor.fetchall()]
cursor.close()

print(f"\n✅ {len(tables)} tables trouvées : {tables}")

list_columns(conn, tables=tables)

disconnect(conn)