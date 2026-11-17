#!/usr/bin/env bash
# Cópia de segurança diária do banco do CampusConnect (contêiner campusconnect_banco). As cópias ficam fora de
# /opt/campus-connect, porque o publicar.sh troca as pastas de código. Guarda os últimos 14 dias. No cron do
# servidor (crontab -e):
#   0 4 * * * bash /opt/campus-connect/deploy/backup_banco.sh >> /var/log/campusconnect-backup.log 2>&1
# Restaurar uma cópia (troca o conteúdo do banco pelo da cópia):
#   docker exec -i campusconnect_banco pg_restore -U campusconnect -d campusconnect --clean --if-exists \
#     < /opt/backups/campusconnect-banco/campusconnect-AAAA-MM-DD.dump
# As fotos e os vídeos enviados ficam no volume campusconnect_uploads e não entram nesta cópia.
set -euo pipefail

DESTINO=/opt/backups/campusconnect-banco
DIAS=14

mkdir -p "$DESTINO"
chmod 700 "$DESTINO"
arquivo="$DESTINO/campusconnect-$(date +%F).dump"
docker exec campusconnect_banco pg_dump -U campusconnect -d campusconnect -Fc > "$arquivo.tmp"
mv "$arquivo.tmp" "$arquivo"
find "$DESTINO" -name 'campusconnect-*.dump' -mtime +"$DIAS" -delete
echo "$(date '+%F %T') cópia em $arquivo ($(du -h "$arquivo" | cut -f1))"
