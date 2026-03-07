"""
Connexion à la base Oracle - TP Informatique Décisionnelle
Prérequis : pip install oracledb
"""

import oracledb

# ─── Paramètres de connexion ───────────────────────────────────────────────────
HOST     = "stendhal.iem"
PORT     = 1521
SERVICE  = "enss2025"
USER     = "ma273150"  
PASSWORD = "ma273150"   


def connect():
    """Établit et retourne une connexion à la base Oracle."""
    dsn = oracledb.makedsn(HOST, PORT, service_name=SERVICE)
    conn = oracledb.connect(user=USER, password=PASSWORD, dsn=dsn)
    print(f"✅ Connecté à Oracle {conn.version} sur {HOST}:{PORT}/{SERVICE}")
    return conn


def disconnect(conn):
    """Ferme proprement la connexion."""
    if conn:
        conn.close()
        print("🔒 Connexion fermée.")


def create_schema(conn, sql_file: str):
    """
    Exécute un fichier .sql pour créer le schéma.
    Les instructions doivent être séparées par des ';'
    """
    with open(sql_file, "r", encoding="utf-8") as f:
        sql = f.read()

    statements = [s.strip() for s in sql.split(";") if s.strip()]

    cursor = conn.cursor()
    for statement in statements:
        try:
            cursor.execute(statement)
            print(f"  ✅ Exécuté : {statement[:60]}...")
        except oracledb.DatabaseError as e:
            error, = e.args
            print(f"  ❌ Erreur : {error.message}")
            print(f"     Sur    : {statement[:60]}...")
    conn.commit()
    cursor.close()
    print("\n✅ Schéma créé avec succès.")


def list_schemas(conn):
    """
    Liste tous les schémas (utilisateurs) disponibles dans la base Oracle.
    Utilise all_users si les droits DBA ne sont pas disponibles.
    """
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT username, account_status, created
            FROM dba_users
            ORDER BY created DESC
        """)
        print(f"\n{'─'*55}")
        print(f"{'SCHÉMA':<30} {'STATUT':<15} {'CRÉÉ LE'}")
        print(f"{'─'*55}")
        for row in cursor.fetchall():
            print(f"{row[0]:<30} {row[1]:<15} {row[2]}")
        print(f"{'─'*55}\n")
    except oracledb.DatabaseError:
        # Fallback si pas de droits DBA
        cursor.execute("SELECT username FROM all_users ORDER BY username")
        print(f"\n{'─'*30}")
        print(f"{'SCHÉMA'}")
        print(f"{'─'*30}")
        for row in cursor.fetchall():
            print(f"  {row[0]}")
        print(f"{'─'*30}\n")
    cursor.close()


def list_tables(conn, schema: str = None):
    """
    Liste les tables disponibles.
    - Si schema est précisé : liste les tables de ce schéma (ex: 'YELP')
    - Sinon : liste les tables de l'utilisateur connecté
    """
    cursor = conn.cursor()
    if schema:
        cursor.execute("""
            SELECT owner, table_name, num_rows
            FROM all_tables
            WHERE owner = :schema
            ORDER BY table_name
        """, schema=schema.upper())
        titre = f"Tables du schéma : {schema.upper()}"
    else:
        cursor.execute("""
            SELECT owner, table_name, num_rows
            FROM user_tables
            ORDER BY table_name
        """)
        titre = "Tables de l'utilisateur connecté"

    rows = cursor.fetchall()
    print(f"\n{'─'*60}")
    print(f"  {titre}")
    print(f"{'─'*60}")
    print(f"{'SCHÉMA':<20} {'TABLE':<30} {'NB LIGNES'}")
    print(f"{'─'*60}")
    if rows:
        for row in rows:
            nb = row[2] if row[2] is not None else "?"
            print(f"{str(row[0]):<20} {row[1]:<30} {nb}")
    else:
        print("  Aucune table trouvée.")
    print(f"{'─'*60}\n")
    cursor.close()

def drop_schema(conn):
    """
    Supprime tous les objets du schema de l'utilisateur connecté :
    vues matérialisées, tables (CASCADE), séquences.
    """
    cursor = conn.cursor()

    # 1. Supprimer les vues matérialisées
    cursor.execute("SELECT mview_name FROM user_mviews")
    mviews = cursor.fetchall()
    for (mview,) in mviews:
        try:
            cursor.execute(f"DROP MATERIALIZED VIEW {mview}")
            print(f"  ✅ Vue matérialisée supprimée : {mview}")
        except Exception as e:
            print(f"  ❌ Erreur vue {mview} : {e}")

    # 2. Supprimer les tables avec CASCADE CONSTRAINTS
    cursor.execute("SELECT table_name FROM user_tables")
    tables = cursor.fetchall()
    for (table,) in tables:
        try:
            cursor.execute(f"DROP TABLE {table} CASCADE CONSTRAINTS")
            print(f"  ✅ Table supprimée : {table}")
        except Exception as e:
            print(f"  ❌ Erreur table {table} : {e}")

    # 3. Supprimer les séquences
    cursor.execute("SELECT sequence_name FROM user_sequences")
    sequences = cursor.fetchall()
    for (seq,) in sequences:
        try:
            cursor.execute(f"DROP SEQUENCE {seq}")
            print(f"  ✅ Séquence supprimée : {seq}")
        except Exception as e:
            print(f"  ❌ Erreur séquence {seq} : {e}")

    conn.commit()
    cursor.close()
    print("\n✅ Schema nettoyé avec succès.")
    
def list_columns(conn, tables: list, schema: str = None):
    """
    Affiche les colonnes d'une liste de tables.
    - tables : liste de noms de tables ex: ['FAIT_USER', 'DIM_REVIEW']
    - schema  : optionnel, ex: 'ma273150'
    """
    cursor = conn.cursor()
    for table_name in tables:
        if schema:
            cursor.execute("""
                SELECT column_name, data_type, data_length, nullable
                FROM all_tab_columns
                WHERE owner = :schema
                AND table_name = :table_name
                ORDER BY column_id
            """, schema=schema.upper(), table_name=table_name.upper())
        else:
            cursor.execute("""
                SELECT column_name, data_type, data_length, nullable
                FROM user_tab_columns
                WHERE table_name = :table_name
                ORDER BY column_id
            """, table_name=table_name.upper())

        rows = cursor.fetchall()
        print(f"\n{'─'*65}")
        print(f"  Colonnes de la table : {table_name.upper()}")
        print(f"{'─'*65}")
        print(f"{'COLONNE':<30} {'TYPE':<20} {'TAILLE':<10} {'NULL?'}")
        print(f"{'─'*65}")
        if rows:
            for row in rows:
                print(f"{row[0]:<30} {row[1]:<20} {str(row[2]):<10} {row[3]}")
        else:
            print("  ⚠️  Aucune colonne trouvée (table inexistante ?).")
        print(f"{'─'*65}\n")
    cursor.close()

# ─── Point d'entrée ────────────────────────────────────────────────────────────
if __name__ == "__main__":
    conn = connect()

    # Exemples d'utilisation :
    # list_schemas(conn)
    # list_tables(conn)                  # tables de ton utilisateur
    # list_tables(conn, schema="yelp")   # tables du schéma yelp
    # create_schema(conn, "schema.sql")

    disconnect(conn)