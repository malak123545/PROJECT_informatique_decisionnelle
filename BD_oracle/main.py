from Data_base_connexion import connect, disconnect, create_schema, list_schemas, list_tables

conn = connect()
# create_schema(conn, "datawarehouse_yelp.sql")
list_schemas(conn)
list_tables(conn, schema="ma273150")
disconnect(conn)
