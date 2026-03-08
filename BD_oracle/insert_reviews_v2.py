import csv, os, sys, time
from datetime import datetime
import oracledb

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
    print(f"Connecte a Oracle {conn.version} sur {HOST}:{PORT}/{SERVICE}", flush=True)
    return conn

def _clean(v):
    if v is None or v == "" or v.lower() == "null":
        return None
    return v

def to_int(v):
    v = _clean(v)
    return int(float(v)) if v is not None else None

def to_float1(v):
    v = _clean(v)
    return round(float(v), 1) if v is not None else None

def to_date(v):
    v = _clean(v)
    if v is None:
        return None
    try:
        return datetime.fromisoformat(v)
    except ValueError:
        try:
            return datetime.strptime(v[:10], "%Y-%m-%d")
        except ValueError:
            return None

def disable_fk(conn):
    print("\n[1/4] Desactivation FK DIM_REVIEW...", flush=True)
    cur = conn.cursor()
    cur.execute("SELECT constraint_name FROM user_constraints WHERE table_name='DIM_REVIEW' AND constraint_type='R'")
    for (fk,) in cur.fetchall():
        cur.execute(f"ALTER TABLE DIM_REVIEW DISABLE CONSTRAINT {fk}")
        print(f"  DISABLE {fk}", flush=True)
    conn.commit(); cur.close()

def enable_fk(conn):
    print("\n[4/4] Reactivation FK DIM_REVIEW...", flush=True)
    cur = conn.cursor()
    cur.execute("SELECT constraint_name FROM user_constraints WHERE table_name='DIM_REVIEW' AND constraint_type='R'")
    for (fk,) in cur.fetchall():
        cur.execute(f"ALTER TABLE DIM_REVIEW ENABLE CONSTRAINT {fk}")
        print(f"  ENABLE {fk}", flush=True)
    conn.commit(); cur.close()

def truncate_dim_review(conn):
    print("\n[2/4] Nettoyage DIM_REVIEW...", flush=True)
    cursor = conn.cursor()
    try:
        cursor.execute("TRUNCATE TABLE DIM_REVIEW")
        print("  DIM_REVIEW videe", flush=True)
    except Exception as e:
        print(f"  DIM_REVIEW : {e}", flush=True)
    conn.commit(); cursor.close()

def insert_dim_review(conn):
    print("\n[3/4] Insertion DIM_REVIEW...", flush=True)
    path = os.path.join(OUTPUT_DIR, "reviews.csv")

    sql = """
        INSERT INTO DIM_REVIEW
            (id_review, review_id, user_id, business_id,
             total_useful, total_funny, total_cool, stars, date_review)
        VALUES (:1, :2, :3, :4, :5, :6, :7, :8, :9)
    """

    cursor = conn.cursor()
    total = 0
    row_num = 0
    batch = []
    t0 = time.time()

    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            row_num += 1   # compteur séquentiel 1..N au lieu du CSV
            batch.append((
                row_num,
                _clean(row[2]),
                _clean(row[0]),
                _clean(row[3]),
                to_int(row[4]),
                to_int(row[5]),
                to_int(row[6]),
                to_float1(row[7]),
                to_date(row[8]),
            ))

            if len(batch) == BATCH_SIZE:
                cursor.executemany(sql, batch)
                total += len(batch)
                batch = []

                if total % 50_000 == 0:
                    conn.commit()
                    elapsed = time.time() - t0
                    rate = total / elapsed if elapsed > 0 else 1
                    pct  = total / 2_375_061 * 100
                    eta  = (2_375_061 - total) / rate / 60
                    print(f"  {total:>9,} lignes  ({pct:5.1f}%)  {rate:,.0f} lig/s  ETA {eta:.1f} min", flush=True)

    if batch:
        cursor.executemany(sql, batch)
        total += len(batch)

    conn.commit()
    cursor.close()
    elapsed = time.time() - t0
    print(f"\n  DIM_REVIEW : {total:,} lignes inserees en {elapsed/60:.1f} min", flush=True)
    return total

def verify(conn):
    cursor = conn.cursor()
    print("\n=== Verification ===", flush=True)
    cursor.execute("SELECT COUNT(*) FROM DIM_REVIEW")
    print(f"  DIM_REVIEW : {cursor.fetchone()[0]:,} lignes", flush=True)
    cursor.close()

if __name__ == "__main__":
    print(f"Dossier CSV : {OUTPUT_DIR}\n", flush=True)
    conn = connect()
    try:
        disable_fk(conn)
        truncate_dim_review(conn)
        insert_dim_review(conn)
        enable_fk(conn)
        verify(conn)
        print("\nInsertion reviews terminee avec succes !", flush=True)
    except Exception as e:
        conn.rollback()
        print(f"\nErreur : {e}", file=sys.stderr)
        raise
    finally:
        conn.close()
        print("Connexion fermee.", flush=True)
