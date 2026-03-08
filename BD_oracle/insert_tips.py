import csv, os, sys, time
from datetime import datetime
import oracledb

HOST, PORT, SERVICE = "stendhal.iem", 1521, "enss2025"
USER, PASSWORD      = "ma273150", "ma273150"
BATCH_SIZE          = 1000

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
    print("\n[1/4] Desactivation FK DIM_TIP...", flush=True)
    cur = conn.cursor()
    cur.execute("SELECT constraint_name FROM user_constraints WHERE table_name='DIM_TIP' AND constraint_type='R'")
    for (fk,) in cur.fetchall():
        cur.execute(f"ALTER TABLE DIM_TIP DISABLE CONSTRAINT {fk}")
        print(f"  DISABLE {fk}", flush=True)
    conn.commit(); cur.close()

def enable_fk(conn):
    print("\n[4/4] Reactivation FK DIM_TIP...", flush=True)
    cur = conn.cursor()
    cur.execute("SELECT constraint_name FROM user_constraints WHERE table_name='DIM_TIP' AND constraint_type='R'")
    for (fk,) in cur.fetchall():
        cur.execute(f"ALTER TABLE DIM_TIP ENABLE CONSTRAINT {fk}")
        print(f"  ENABLE {fk}", flush=True)
    conn.commit(); cur.close()

def truncate(conn):
    print("\n[2/4] Nettoyage DIM_TIP...", flush=True)
    cur = conn.cursor()
    cur.execute("TRUNCATE TABLE DIM_TIP")
    conn.commit(); cur.close()
    print("  DIM_TIP videe", flush=True)

def insert_dim_tip(conn):
    print("\n[3/4] Insertion DIM_TIP...", flush=True)
    path = os.path.join(OUTPUT_DIR, "tips.csv")

    sql = """
        INSERT INTO DIM_TIP (id_tip, user_id, business_id, date_tip, compliment_count)
        VALUES (:1, :2, :3, :4, :5)
    """

    cur = conn.cursor()
    total, row_num, batch = 0, 0, []
    t0 = time.time()

    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            row_num += 1
            batch.append((
                row_num,           # id_tip séquentiel
                _clean(row[1]),    # user_id
                _clean(row[2]),    # business_id
                to_date(row[3]),   # date_tip
                to_int(row[4]),    # compliment_count
            ))

            if len(batch) == BATCH_SIZE:
                cur.executemany(sql, batch)
                total += len(batch)
                batch = []
                elapsed = time.time() - t0
                rate = total / elapsed if elapsed > 0 else 1
                print(f"  {total:>7,} lignes  {rate:,.0f} lig/s", flush=True)

    if batch:
        cur.executemany(sql, batch)
        total += len(batch)

    conn.commit()
    cur.close()
    elapsed = time.time() - t0
    print(f"\n  DIM_TIP : {total:,} lignes inserees en {elapsed:.1f}s", flush=True)
    return total

def verify(conn):
    cur = conn.cursor()
    print("\n=== Verification ===", flush=True)
    cur.execute("SELECT COUNT(*) FROM DIM_TIP")
    print(f"  DIM_TIP : {cur.fetchone()[0]:,} lignes", flush=True)

    # Orphelins
    cur.execute("SELECT COUNT(*) FROM DIM_TIP WHERE NOT EXISTS (SELECT 1 FROM FAIT_USER u WHERE u.user_id = DIM_TIP.user_id)")
    print(f"  Orphelins user_id     : {cur.fetchone()[0]:,}", flush=True)
    cur.execute("SELECT COUNT(*) FROM DIM_TIP WHERE NOT EXISTS (SELECT 1 FROM FAIT_BUSINESS b WHERE b.business_id = DIM_TIP.business_id)")
    print(f"  Orphelins business_id : {cur.fetchone()[0]:,}", flush=True)
    cur.close()

if __name__ == "__main__":
    print(f"Dossier CSV : {OUTPUT_DIR}\n", flush=True)
    conn = connect()
    try:
        disable_fk(conn)
        truncate(conn)
        insert_dim_tip(conn)
        enable_fk(conn)
        verify(conn)
        print("\nInsertion DIM_TIP terminee avec succes !", flush=True)
    except Exception as e:
        conn.rollback()
        print(f"\nErreur : {e}", file=sys.stderr)
        raise
    finally:
        conn.close()
        print("Connexion fermee.", flush=True)
