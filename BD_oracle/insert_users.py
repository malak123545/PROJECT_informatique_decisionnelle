"""
Insertion des utilisateurs dans Oracle
  FAIT_USER  ← users.csv (737 667 lignes)

user_elite.csv et user_friends.csv ne correspondent pas à des tables Oracle
distinctes : leurs données agrégées (nb_annees_elite, derniere_annee_elite,
friend_count) sont déjà dans users.csv.
"""

import csv
import os
import sys
from datetime import datetime, timezone
import oracledb

# ── Connexion ────────────────────────────────────────────────────────────────
HOST     = "stendhal.iem"
PORT     = 1521
SERVICE  = "enss2025"
USER     = "ma273150"
PASSWORD = "ma273150"

BATCH_SIZE = 1000


def _find_output_dir():
    for entry in os.scandir('/home/preconys/Images'):
        if entry.inode() == 44047924:
            return entry.path + '/PROJECT_informatique_decisionnelle/ETL_Json/output'
    raise FileNotFoundError("Dossier projet introuvable")

OUTPUT_DIR = _find_output_dir()


def connect():
    dsn = oracledb.makedsn(HOST, PORT, service_name=SERVICE)
    conn = oracledb.connect(user=USER, password=PASSWORD, dsn=dsn)
    print(f"Connecté à Oracle {conn.version} sur {HOST}:{PORT}/{SERVICE}")
    return conn


# ── Conversions ──────────────────────────────────────────────────────────────
def _clean(v):
    if v is None or v == "" or v.lower() == "null":
        return None
    return v


def to_int(v):
    v = _clean(v)
    return int(float(v)) if v is not None else None


def to_float4(v):
    v = _clean(v)
    return round(float(v), 4) if v is not None else None


def to_date(v):
    """Parse ISO 8601 (ex: '2014-11-02T14:53:16.000+01:00') → datetime Python."""
    v = _clean(v)
    if v is None:
        return None
    # Normalise le format timezone (+01:00 → aware datetime)
    try:
        # Python 3.7+ gère %z avec ':'
        return datetime.fromisoformat(v)
    except ValueError:
        # Fallback : on prend juste la date (avant le T)
        try:
            return datetime.strptime(v[:10], "%Y-%m-%d")
        except ValueError:
            return None


# ── Insert FAIT_USER ─────────────────────────────────────────────────────────
def insert_fait_user(conn):
    print("\n[1/1] FAIT_USER...")
    path = os.path.join(OUTPUT_DIR, "users.csv")

    # CSV columns (dans l'ordre) :
    # 0:user_id 1:name 2:yelping_since 3:fans 4:compliment_hot 5:compliment_more
    # 6:compliment_profile 7:compliment_cute 8:compliment_list 9:compliment_note
    # 10:compliment_plain 11:compliment_cool 12:compliment_funny 13:friend_count
    # 14:derniere_annee_elite 15:nb_annees_elite 16:review_count
    # 17:average_stars 18:useful 19:funny 20:cool

    sql = """
        INSERT INTO FAIT_USER (
            user_id, name, yelping_since, fans,
            compliment_hot, compliment_more, compliment_profile, compliment_cute,
            compliment_list, compliment_note, compliment_plain, compliment_cool,
            compliment_funny,
            friend_count, derniere_annee_elite, nb_annees_elite,
            review_count, average_stars, useful, funny, cool
        ) VALUES (
            :1, :2, :3, :4,
            :5, :6, :7, :8,
            :9, :10, :11, :12,
            :13,
            :14, :15, :16,
            :17, :18, :19, :20, :21
        )
    """

    cursor = conn.cursor()
    total = 0
    batch = []

    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader)  # skip header
        for row in reader:
            batch.append((
                _clean(row[0]),           # user_id
                _clean(row[1]),           # name
                to_date(row[2]),          # yelping_since → DATE
                to_int(row[3]),           # fans
                to_int(row[4]),           # compliment_hot
                to_int(row[5]),           # compliment_more
                to_int(row[6]),           # compliment_profile
                to_int(row[7]),           # compliment_cute
                to_int(row[8]),           # compliment_list
                to_int(row[9]),           # compliment_note
                to_int(row[10]),          # compliment_plain
                to_int(row[11]),          # compliment_cool
                to_int(row[12]),          # compliment_funny
                to_int(row[13]),          # friend_count
                to_int(row[14]),          # derniere_annee_elite
                to_int(row[15]),          # nb_annees_elite
                to_int(row[16]),          # review_count
                to_float4(row[17]),       # average_stars
                to_int(row[18]),          # useful
                to_int(row[19]),          # funny
                to_int(row[20]),          # cool
            ))

            if len(batch) == BATCH_SIZE:
                cursor.executemany(sql, batch)
                total += len(batch)
                batch = []
                if total % 100_000 == 0:
                    conn.commit()
                    print(f"  {total:>8} lignes insérées...")

    if batch:
        cursor.executemany(sql, batch)
        total += len(batch)

    conn.commit()
    cursor.close()
    print(f"  FAIT_USER : {total} lignes insérées au total")
    return total


# ── Nettoyage préalable ───────────────────────────────────────────────────────
def truncate_fait_user(conn):
    print("\n[0/1] Nettoyage FAIT_USER...")
    cursor = conn.cursor()
    # DIM_REVIEW et DIM_TIP ont des FK sur FAIT_USER — on les vide aussi
    for t in ["DIM_TIP", "DIM_REVIEW", "FAIT_USER"]:
        try:
            cursor.execute(f"TRUNCATE TABLE {t}")
            print(f"  {t} vidée")
        except Exception as e:
            print(f"  {t} : {e}")
    conn.commit()
    cursor.close()


# ── Vérification ─────────────────────────────────────────────────────────────
def verify(conn):
    cursor = conn.cursor()
    print("\n=== Vérification ===")
    cursor.execute("SELECT COUNT(*) FROM FAIT_USER")
    print(f"  FAIT_USER : {cursor.fetchone()[0]} lignes")
    cursor.execute("SELECT COUNT(*) FROM FAIT_USER WHERE yelping_since IS NOT NULL")
    print(f"  FAIT_USER (yelping_since renseigné) : {cursor.fetchone()[0]} lignes")
    cursor.close()


# ── Main ─────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print(f"Dossier CSV : {OUTPUT_DIR}\n")
    conn = connect()
    try:
        truncate_fait_user(conn)
        insert_fait_user(conn)
        verify(conn)
        print("\nInsertion users terminée avec succès !")
    except Exception as e:
        conn.rollback()
        print(f"\nErreur : {e}", file=sys.stderr)
        raise
    finally:
        conn.close()
        print("Connexion fermée.")
