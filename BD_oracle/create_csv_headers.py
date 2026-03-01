#!/usr/bin/env python3
"""Génère des fichiers CSV contenant uniquement la ligne d'en-tête pour chaque CSV trouvé.

Usage:
  python create_csv_headers.py /path/to/csvs --outdir heads

Options:
  --recursive   : chercher récursivement
  --suffix STR  : suffixe ajouté au nom de sortie (par défaut: _head.csv)
  --overwrite   : écrase les fichiers existants
"""
import argparse
from pathlib import Path
import csv


def copy_header(src_path: Path, out_dir: Path, suffix: str, overwrite: bool) -> bool:
    out_dir.mkdir(parents=True, exist_ok=True)
    try:
        with src_path.open("r", encoding="utf-8-sig", newline="") as f:
            reader = csv.reader(f)
            header = next(reader)
    except StopIteration:
        return False
    out_path = out_dir / (src_path.stem + suffix)
    if out_path.exists() and not overwrite:
        return False
    with out_path.open("w", encoding="utf-8", newline="") as out:
        writer = csv.writer(out)
        writer.writerow(header)
    return True


def main():
    p = argparse.ArgumentParser(description="Créer des fichiers CSV contenant uniquement l'en-tête")
    p.add_argument("src", help="Dossier contenant les fichiers CSV")
    p.add_argument("--outdir", default="heads", help="Dossier de sortie (par défaut: heads)")
    p.add_argument("--recursive", action="store_true", help="Parcourir les sous-dossiers")
    p.add_argument("--suffix", default="_head.csv", help="Suffixe pour les fichiers de sortie")
    p.add_argument("--overwrite", action="store_true", help="Écrase les fichiers existants")
    args = p.parse_args()

    src_dir = Path(args.src)
    out_dir = src_dir / args.outdir if not Path(args.outdir).is_absolute() else Path(args.outdir)

    pattern = "**/*.csv" if args.recursive else "*.csv"
    files = list(src_dir.glob(pattern))
    if not files:
        print("Aucun fichier CSV trouvé dans", src_dir)
        return

    created = 0
    skipped = 0
    empty = 0
    for f in files:
        ok = copy_header(f, out_dir, args.suffix, args.overwrite)
        if ok:
            created += 1
        else:
            # déterminer si fichier vide ou existant non écrasé
            try:
                with f.open("r", encoding="utf-8-sig") as fh:
                    if not fh.readline():
                        empty += 1
                    else:
                        skipped += 1
            except Exception:
                skipped += 1

    print(f"Créés: {created}, Ignorés (existant/non écrasé): {skipped}, Vides: {empty}")


if __name__ == "__main__":
    main()
