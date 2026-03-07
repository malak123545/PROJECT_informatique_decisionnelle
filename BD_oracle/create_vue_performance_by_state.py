"""
Déploiement de la vue V_PERFORMANCE_BY_STATE
Scénario 2 — Cartographie des opportunités commerciales par métropole

Usage :
    python BD_oracle/create_vue_performance_by_state.py
"""

import sys
import os

sys.path.insert(0, os.path.dirname(__file__))
from Data_base_connexion import connect, disconnect


SQL_FILE = os.path.join(os.path.dirname(__file__), "vue_performance_by_state.sql")


def create_view(conn):
    """Lit le fichier SQL et exécute le CREATE OR REPLACE VIEW."""
    with open(SQL_FILE, "r", encoding="utf-8") as f:
        sql = f.read()

    sql = sql.strip().rstrip(";")

    cursor = conn.cursor()
    try:
        cursor.execute(sql)
        conn.commit()
        print("Vue V_PERFORMANCE_BY_STATE créée avec succès.")
    except Exception as e:
        print(f"Erreur lors de la création de la vue : {e}")
        raise
    finally:
        cursor.close()


def preview_view(conn):
    """Affiche le meilleur et le pire type de business par state."""
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT
                state,
                type_business,
                nb_business,
                avg_stars,
                total_reviews,
                statut
            FROM V_PERFORMANCE_BY_STATE
            WHERE statut != '-'
            ORDER BY state, statut
        """)
        rows = cursor.fetchall()
        cols = [desc[0] for desc in cursor.description]

        print(f"\n{'─' * 90}")
        print("  Meilleur et pire type de business par state")
        print(f"{'─' * 90}")
        print("  " + "  ".join(f"{c:<20}" for c in cols))
        print(f"{'─' * 90}")
        for row in rows:
            print("  " + "  ".join(f"{str(v):<20}" for v in row))
        print(f"{'─' * 90}\n")

    except Exception as e:
        print(f"Erreur lors de la lecture de la vue : {e}")
    finally:
        cursor.close()


if __name__ == "__main__":
    conn = connect()
    create_view(conn)
    preview_view(conn)
    disconnect(conn)
