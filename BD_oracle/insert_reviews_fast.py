import os, sys, time, threading
import pandas as pd
import oracledb

HOST, PORT, SERVICE = "stendhal.iem", 1521, "enss2025"
USER, PASSWORD      = "ma273150", "ma273150"
BATCH_SIZE          = 100_000
N_THREADS           = 4

lock = threading.Lock()
total_inserted = 0
total_skipped  = 0

def log(msg):
    print(msg, flush=True)

def get_output_dir():
    for e in os.scandir('/home/preconys/Images'):
        if e.inode() == 44047924:
            return os.path.join(e.path, "PROJECT_informatique_decisionnelle/ETL_Json/output")

def connect():
    dsn = oracledb.makedsn(HOST, PORT, service_name=SERVICE)
    return oracledb.connect(user=USER, password=PASSWORD, dsn=dsn)

def set_fk(action):
    conn = connect()
    cur = conn.cursor()
    cur.execute("SELECT constraint_name FROM user_constraints WHERE table_name='DIM_REVIEW' AND constraint_type='R'")
    for (fk,) in cur.fetchall():
        cur.execute(f"ALTER TABLE DIM_REVIEW {action} CONSTRAINT {fk}")
        log(f"  {action} {fk}")
    conn.commit(); cur.close(); conn.close()

def insert_chunk(chunk_df, thread_id, t0_global):
    global total_inserted, total_skipped
    conn = connect()
    cur  = conn.cursor()
    sql  = """INSERT INTO DIM_REVIEW
                (id_review,review_id,user_id,business_id,
                 total_useful,total_funny,total_cool,stars,date_review)
              VALUES (:1,:2,:3,:4,:5,:6,:7,:8,:9)"""

    rows = []
    for _, r in chunk_df.iterrows():
        id_rev = None if pd.isna(r['id_review']) else int(r['id_review']) + 1
        date_v = None
        if not pd.isna(r['date_review']):
            try:    date_v = pd.to_datetime(r['date_review']).to_pydatetime()
            except: pass
        rows.append((
            id_rev,
            None if pd.isna(r['review_id'])    else str(r['review_id']),
            None if pd.isna(r['user_id'])       else str(r['user_id']),
            None if pd.isna(r['business_id'])   else str(r['business_id']),
            None if pd.isna(r['total_useful'])  else int(r['total_useful']),
            None if pd.isna(r['total_funny'])   else int(r['total_funny']),
            None if pd.isna(r['total_cool'])    else int(r['total_cool']),
            None if pd.isna(r['stars'])         else round(float(r['stars']),1),
            date_v,
        ))

    inserted = skipped = 0
    for i in range(0, len(rows), BATCH_SIZE):
        batch = rows[i:i+BATCH_SIZE]
        try:
            cur.executemany(sql, batch, batcherrors=True)
            errs = cur.getbatcherrors()
            inserted += len(batch) - len(errs)
            skipped  += len(errs)
        except Exception as e:
            skipped += len(batch)
            log(f"  [T{thread_id}] Erreur batch : {e}")
        conn.commit()

        with lock:
            total_inserted += len(batch) - (len(errs) if 'errs' in dir() else 0)
            total_skipped  += len(errs)  if 'errs' in dir() else len(batch)
            ti = total_inserted
        elapsed = time.time() - t0_global
        rate = ti / elapsed if elapsed > 0 else 1
        pct  = ti / 2_375_061 * 100
        eta  = (2_375_061 - ti) / rate / 60 if rate > 0 else 0
        log(f"  [T{thread_id}] +{len(batch):,} → total={ti:,} ({pct:.1f}%) | {rate:,.0f} lig/s | ETA {eta:.1f} min")

    cur.close(); conn.close()
    log(f"  [T{thread_id}] TERMINE : {inserted:,} inserees, {skipped:,} ignorees")

if __name__ == "__main__":
    output_dir = get_output_dir()
    path = os.path.join(output_dir, "reviews.csv")

    log("=== INSERT DIM_REVIEW ===")
    log(f"Fichier : {path}\n")

    # 1. Truncate
    log("[1/4] TRUNCATE DIM_REVIEW...")
    conn = connect(); cur = conn.cursor()
    cur.execute("TRUNCATE TABLE DIM_REVIEW")
    conn.commit(); cur.close(); conn.close()
    log("  OK\n")

    # 2. Disable FK
    log("[2/4] Desactivation FK...")
    set_fk("DISABLE")
    log("  OK\n")

    # 3. Lire le CSV avec pandas (rapide)
    log("[3/4] Lecture CSV...")
    df = pd.read_csv(path, dtype=str, keep_default_na=False, na_values=['', 'null', 'NULL'])
    df.columns = ['user_id','id_review','review_id','business_id','total_useful','total_funny','total_cool','stars','date_review']
    log(f"  {len(df):,} lignes chargees\n")

    # 4. Insertion en parallele
    log(f"[4/4] Insertion ({N_THREADS} threads x {BATCH_SIZE:,})...")
    chunks = [df.iloc[i::N_THREADS] for i in range(N_THREADS)]
    t0 = time.time()
    threads = [threading.Thread(target=insert_chunk, args=(chunks[i], i+1, t0)) for i in range(N_THREADS)]
    for t in threads: t.start()
    for t in threads: t.join()

    # 5. Réactiver FK
    log("\n[5/5] Reactivation FK...")
    set_fk("ENABLE")

    # Résultat final
    conn = connect(); cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM DIM_REVIEW")
    final = cur.fetchone()[0]
    elapsed = time.time() - t0
    log(f"\n=== TERMINE : {final:,} lignes en {elapsed/60:.1f} min ===")
    cur.close(); conn.close()
