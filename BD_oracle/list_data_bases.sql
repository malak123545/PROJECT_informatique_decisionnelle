-- ─── Lister tous les schémas (utilisateurs) de la base Oracle ─────────────────
SELECT username, account_status, created
FROM dba_users
ORDER BY created DESC;