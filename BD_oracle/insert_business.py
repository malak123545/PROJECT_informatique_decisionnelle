"""
Insertion des données business dans Oracle
Ordre d'insertion (respect des FK) :
  1. DIM_LOCALISATION   ← localisation.csv
  2. DIM_TYPE_BUSINESS  ← business_types.csv
  3. FAIT_BUSINESS      ← business.csv      (FK parking_id désactivée temporairement)
  4. DIM_PARKING        ← parking.csv
  5. Réactivation FK parking_id
  6. DIM_HORAIRE        ← hours.csv
  7. DIM_CATEGORIE      ← categories.csv

Note : les IDs Spark (monotonically_increasing_id) sont re-numérotés séquentiellement
       pour respecter NUMBER(10) Oracle.
"""

import csv
import os
import sys
import oracledb

# ── Connexion ────────────────────────────────────────────────────────────────
HOST     = "stendhal.iem"
PORT     = 1521
SERVICE  = "enss2025"
USER     = "ma273150"
PASSWORD = "ma273150"

BATCH_SIZE = 500


# ── Chemin vers les CSV (gestion apostrophe Unicode U+2019 dans le nom du dossier) ──
def _find_output_dir():
    images = '/home/preconys/Images'
    for entry in os.scandir(images):
        if entry.inode() == 44047924:
            return entry.path + '/PROJECT_informatique_decisionnelle/ETL_Json/output'
    raise FileNotFoundError("Dossier projet introuvable")

OUTPUT_DIR = _find_output_dir()


# ── Utilitaires ──────────────────────────────────────────────────────────────
def connect():
    dsn = oracledb.makedsn(HOST, PORT, service_name=SERVICE)
    conn = oracledb.connect(user=USER, password=PASSWORD, dsn=dsn)
    print(f"Connecté à Oracle {conn.version} sur {HOST}:{PORT}/{SERVICE}")
    return conn


def _clean(val):
    """Retourne None pour les chaînes vides ou 'null'."""
    if val is None or val == "" or val.lower() == "null":
        return None
    return val


def to_int(v):
    v = _clean(v)
    if v is None:
        return None
    try:
        return int(float(v))
    except (ValueError, TypeError):
        return None


def to_float6(v):
    """Float arrondi à 6 décimales (pour NUMBER(19,6))."""
    v = _clean(v)
    if v is None:
        return None
    try:
        return round(float(v), 6)
    except (ValueError, TypeError):
        return None


def to_float4(v):
    """Float arrondi à 4 décimales (pour NUMBER(19,4))."""
    v = _clean(v)
    if v is None:
        return None
    try:
        return round(float(v), 4)
    except (ValueError, TypeError):
        return None


def read_csv(filename):
    """Retourne (headers, rows) bruts."""
    path = os.path.join(OUTPUT_DIR, filename)
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        headers = next(reader)
        rows = [row for row in reader]
    print(f"  {filename}: {len(rows)} lignes lues")
    return headers, rows


def insert_batch(cursor, sql, rows, desc=""):
    total = 0
    for i in range(0, len(rows), BATCH_SIZE):
        batch = rows[i:i + BATCH_SIZE]
        cursor.executemany(sql, batch)
        total += len(batch)
    print(f"  {desc}: {total} lignes insérées")


# ── Étape 1 : DIM_LOCALISATION ───────────────────────────────────────────────
def insert_localisation(conn):
    """
    Retourne un dict {old_spark_id: new_seq_id} pour remapper dans FAIT_BUSINESS.
    """
    print("\n[1/7] DIM_LOCALISATION...")
    _, rows = read_csv("localisation.csv")

    loc_map = {}   # old_spark_id (str) -> new sequential int
    tuples = []
    for new_id, row in enumerate(rows, start=1):
        old_id = row[0]
        loc_map[old_id] = new_id
        tuples.append((
            new_id,
            _clean(row[1]),   # city
            _clean(row[2]),   # state
            _clean(row[3]),   # postal_code
            _clean(row[4]),   # address
            to_float6(row[5]),  # latitude
            to_float6(row[6]),  # longitude
        ))

    sql = """
        INSERT INTO DIM_LOCALISATION
            (localisation_id, city, state, postal_code, address, latitude, longitude)
        VALUES (:1, :2, :3, :4, :5, :6, :7)
    """
    cursor = conn.cursor()
    insert_batch(cursor, sql, tuples, "DIM_LOCALISATION")
    conn.commit()
    cursor.close()
    return loc_map


# ── Étape 2 : DIM_TYPE_BUSINESS ──────────────────────────────────────────────
def insert_type_business(conn):
    print("\n[2/7] DIM_TYPE_BUSINESS...")
    _, rows = read_csv("business_types.csv")
    tuples = [(to_int(r[0]), _clean(r[1])) for r in rows]
    sql = "INSERT INTO DIM_TYPE_BUSINESS (type_id, type_name) VALUES (:1, :2)"
    cursor = conn.cursor()
    insert_batch(cursor, sql, tuples, "DIM_TYPE_BUSINESS")
    conn.commit()
    cursor.close()


# ── Étape 3 : FAIT_BUSINESS (FK parking désactivée) ─────────────────────────
def insert_fait_business(conn, loc_map, park_map):
    """
    loc_map  : {old_spark_loc_id  -> new_seq_id}
    park_map : {old_spark_park_id -> new_seq_id}  (pré-construit depuis parking.csv)
    """
    print("\n[3/7] FAIT_BUSINESS...")
    _, rows = read_csv("business.csv")

    tuples = []
    for row in rows:
        old_loc  = row[2]
        old_park = row[4]
        new_loc  = loc_map.get(old_loc)   if old_loc  else None
        new_park = park_map.get(old_park) if old_park else None

        tuples.append((
            _clean(row[0]),       # business_id
            _clean(row[1]),       # name
            new_loc,              # localisation_id (remappé)
            to_int(row[3]),       # type_id
            new_park,             # parking_id (remappé)
            to_float4(row[5]),    # stars
            to_int(row[6]),       # review_count
        ))

    cursor = conn.cursor()
    cursor.execute("ALTER TABLE FAIT_BUSINESS DISABLE CONSTRAINT FK_FB_PARKING")

    sql = """
        INSERT INTO FAIT_BUSINESS
            (business_id, name, localisation_id, type_id, parking_id, stars, review_count)
        VALUES (:1, :2, :3, :4, :5, :6, :7)
    """
    insert_batch(cursor, sql, tuples, "FAIT_BUSINESS")
    conn.commit()
    cursor.close()


# ── Pré-construction du mapping parking ──────────────────────────────────────
def build_park_map():
    """Lit parking.csv et retourne {old_spark_id: new_seq_id} sans rien insérer."""
    _, rows = read_csv("parking.csv")
    return {row[1]: i + 1 for i, row in enumerate(rows)}


# ── Étape 4 : DIM_PARKING ────────────────────────────────────────────────────
def insert_parking(conn, park_map):
    """Insère DIM_PARKING en utilisant les IDs séquentiels déjà construits."""
    print("\n[4/7] DIM_PARKING...")
    _, rows = read_csv("parking.csv")

    tuples = [
        (
            park_map[row[1]],     # parking_id (new seq)
            _clean(row[0]),       # business_id
            to_int(row[2]),       # garage
            to_int(row[3]),       # street
            to_int(row[4]),       # lot
            to_int(row[5]),       # paid
            to_int(row[6]),       # validated
            to_int(row[7]),       # valet
        )
        for row in rows
    ]

    sql = """
        INSERT INTO DIM_PARKING
            (parking_id, business_id, garage, street, lot, paid, validated, valet)
        VALUES (:1, :2, :3, :4, :5, :6, :7, :8)
    """
    cursor = conn.cursor()
    insert_batch(cursor, sql, tuples, "DIM_PARKING")
    conn.commit()
    cursor.close()


# ── Étape 5 : Réactivation FK parking ────────────────────────────────────────
def enable_parking_fk(conn):
    print("\n[5/7] Réactivation contrainte FK_FB_PARKING...")
    cursor = conn.cursor()
    cursor.execute("ALTER TABLE FAIT_BUSINESS ENABLE CONSTRAINT FK_FB_PARKING")
    conn.commit()
    cursor.close()
    print("  FK_FB_PARKING réactivée")


# ── Étape 6 : DIM_HORAIRE ────────────────────────────────────────────────────
def insert_horaire(conn):
    print("\n[6/7] DIM_HORAIRE...")
    _, rows = read_csv("hours.csv")
    tuples = [tuple(_clean(v) for v in row) for row in rows]
    sql = """
        INSERT INTO DIM_HORAIRE (
            business_id,
            monday_opening, monday_closing,
            tuesday_opening, tuesday_closing,
            wednesday_opening, wednesday_closing,
            thursday_opening, thursday_closing,
            friday_opening, friday_closing,
            saturday_opening, saturday_closing,
            sunday_opening, sunday_closing
        ) VALUES (:1,:2,:3,:4,:5,:6,:7,:8,:9,:10,:11,:12,:13,:14,:15)
    """
    cursor = conn.cursor()
    insert_batch(cursor, sql, tuples, "DIM_HORAIRE")
    conn.commit()
    cursor.close()


# ── Étape 7 : DIM_CATEGORIE ──────────────────────────────────────────────────
def insert_categorie(conn):
    print("\n[7/7] DIM_CATEGORIE...")
    # CSV: business_id, categorie_id, categorie_name
    _, rows = read_csv("categories.csv")
    # Re-numérotation séquentielle des categorie_id
    tuples = [
        (i + 1, _clean(row[0]), _clean(row[2]))
        for i, row in enumerate(rows)
    ]
    sql = """
        INSERT INTO DIM_CATEGORIE (categorie_id, business_id, categorie_name)
        VALUES (:1, :2, :3)
    """
    cursor = conn.cursor()
    insert_batch(cursor, sql, tuples, "DIM_CATEGORIE")
    conn.commit()
    cursor.close()


# ── Nettoyage préalable ───────────────────────────────────────────────────────
def truncate_tables(conn):
    """Vide les tables dans l'ordre inverse des FK pour repartir de zéro."""
    print("\n[0/7] Nettoyage des tables...")
    # Ordre inverse des dépendances
    tables = [
        "DIM_CATEGORIE",
        "DIM_HORAIRE",
        "DIM_PARKING",
        "FAIT_BUSINESS",
        "DIM_TYPE_BUSINESS",
        "DIM_LOCALISATION",
    ]
    cursor = conn.cursor()
    # Désactiver la FK circulaire d'abord
    try:
        cursor.execute("ALTER TABLE FAIT_BUSINESS DISABLE CONSTRAINT FK_FB_PARKING")
    except Exception:
        pass
    for t in tables:
        try:
            cursor.execute(f"TRUNCATE TABLE {t}")
            print(f"  {t} vidée")
        except Exception as e:
            print(f"  {t} : {e}")
    conn.commit()
    cursor.close()


# ── Vérification finale ───────────────────────────────────────────────────────
def verify(conn):
    tables = [
        "DIM_LOCALISATION", "DIM_TYPE_BUSINESS", "FAIT_BUSINESS",
        "DIM_PARKING", "DIM_HORAIRE", "DIM_CATEGORIE"
    ]
    cursor = conn.cursor()
    print("\n=== Vérification ===")
    for t in tables:
        cursor.execute(f"SELECT COUNT(*) FROM {t}")
        count = cursor.fetchone()[0]
        print(f"  {t:<25}: {count:>8} lignes")
    cursor.close()


# ── Main ─────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print(f"Dossier CSV : {OUTPUT_DIR}\n")
    conn = connect()
    try:
        truncate_tables(conn)

        print("\n[pré-traitement] Construction du mapping parking_id...")
        park_map = build_park_map()
        print(f"  {len(park_map)} parking_id mappés")

        loc_map = insert_localisation(conn)
        insert_type_business(conn)
        insert_fait_business(conn, loc_map, park_map)
        insert_parking(conn, park_map)
        enable_parking_fk(conn)
        insert_horaire(conn)
        insert_categorie(conn)
        verify(conn)
        print("\nInsertion business terminée avec succès !")
    except Exception as e:
        conn.rollback()
        print(f"\nErreur : {e}", file=sys.stderr)
        raise
    finally:
        conn.close()
        print("Connexion fermée.")
