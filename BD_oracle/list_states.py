"""
Listing des states présents dans la base Oracle
Scénario 2 — Cartographie des opportunités commerciales par métropole

Usage :
    python BD_oracle/list_states.py
"""

import sys
import os

sys.path.insert(0, os.path.dirname(__file__))
from Data_base_connexion import connect, disconnect


def list_states(conn):
    """Liste les states distincts avec le nombre de business associés."""
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT
                l.state,
                COUNT(b.business_id)    AS nb_business,
                ROUND(AVG(b.stars), 2)  AS avg_stars,
                SUM(b.review_count)     AS total_reviews
            FROM FAIT_BUSINESS b
            JOIN DIM_LOCALISATION l ON b.localisation_id = l.localisation_id
            WHERE l.state IS NOT NULL
            GROUP BY l.state
            ORDER BY nb_business DESC
        """)
        rows = cursor.fetchall()

        print(f"\n{'─' * 65}")
        print(f"  States présents dans la base ({len(rows)} états)")
        print(f"{'─' * 65}")
        print(f"  {'STATE':<15} {'NB BUSINESS':>12} {'AVG STARS':>10} {'TOTAL REVIEWS':>14}")
        print(f"{'─' * 65}")
        for row in rows:
            print(f"  {str(row[0]):<15} {row[1]:>12} {str(row[2]):>10} {row[3]:>14}")
        print(f"{'─' * 65}\n")

    except Exception as e:
        print(f"Erreur : {e}")
    finally:
        cursor.close()


if __name__ == "__main__":
    conn = connect()
    list_states(conn)
    disconnect(conn)
