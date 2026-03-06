"""
Déploiement de la vue V_TOP_USERS_BY_BUSINESS_TYPE
Scénario 1 — Récompenser les users contributeurs

Usage :
    python BD_Oracle/create_vue_top_users.py
"""

import sys
import os

sys.path.insert(0, os.path.dirname(__file__))
from Data_base_connexion import connect, disconnect


SQL_FILE = os.path.join(os.path.dirname(__file__), "vue_top_users_by_business_type.sql")


def create_view(conn):
    """Lit le fichier SQL et exécute le CREATE OR REPLACE VIEW."""
    with open(SQL_FILE, "r", encoding="utf-8") as f:
        sql = f.read()

    # Oracle n'accepte pas le point-virgule final dans execute()
    sql = sql.strip().rstrip(";")

    cursor = conn.cursor()
    try:
        cursor.execute(sql)
        conn.commit()
        print("Vue V_TOP_USERS_BY_BUSINESS_TYPE créée avec succès.")
    except Exception as e:
        print(f"Erreur lors de la création de la vue : {e}")
        raise
    finally:
        cursor.close()


def preview_view(conn, limit: int = 20):
    """Affiche un aperçu des résultats de la vue."""
    cursor = conn.cursor()
    try:
        cursor.execute(f"""
            SELECT
                type_business,
                rang,
                nom_user,
                score_pertinence,
                nb_amis,
                fans,
                nb_annees_elite,
                derniere_annee_elite,
                best_review_useful,
                best_review_date
            FROM V_TOP_USERS_BY_BUSINESS_TYPE
            WHERE rang <= 5
            ORDER BY type_business, rang
            FETCH FIRST {limit} ROWS ONLY
        """)
        rows = cursor.fetchall()
        cols = [desc[0] for desc in cursor.description]

        print(f"\n{'─' * 130}")
        print("  Aperçu — Top 5 par type de business")
        print(f"{'─' * 130}")
        print("  " + "  ".join(f"{c:<20}" for c in cols))
        print(f"{'─' * 130}")
        for row in rows:
            print("  " + "  ".join(f"{str(v):<20}" for v in row))
        print(f"{'─' * 130}\n")
    except Exception as e:
        print(f"Erreur lors de la lecture de la vue : {e}")
    finally:
        cursor.close()


if __name__ == "__main__":
    conn = connect()
    create_view(conn)
    preview_view(conn)
    disconnect(conn)
