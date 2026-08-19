# DEPLOYMENT.md

Cómo está desplegado `agent-project` en `nolost-vps`. **Desplegado y verificado el 2026-08-19** (`https://agent.srdejo.com.co`) — esto ya no es solo una guía, es la config real corriendo.

## Prerrequisitos en el VPS (ya cumplidos)

- PostgreSQL: instancia existente del VPS (la misma que usa `nolost`), con base y usuario propios:
  ```sql
  CREATE DATABASE agent_project;
  CREATE USER agent_project WITH PASSWORD '<password-real, ver .env en el servidor>';
  GRANT ALL PRIVILEGES ON DATABASE agent_project TO agent_project;
  ALTER DATABASE agent_project OWNER TO agent_project;
  ```
- Java 21 instalado.
- nginx ya instalado y en uso (mismo que sirve `nolost` y los demás sitios).
- DNS: `agent.srdejo.com.co` → IP del VPS (`207.38.88.222`), registro A creado por el usuario.
- Certificado SSL: `sudo certbot --nginx -d agent.srdejo.com.co`.

## Puerto

Backend en **`127.0.0.1:8083`** (loopback, nginx hace proxy) — `nolost` ya ocupa 8080 en el mismo servidor. Ver `PORTS.md` en la raíz del workspace para el mapa completo de puertos del VPS; actualizarlo cada vez que se despliega un servicio nuevo.

## Estructura de directorios

```
/home/srdejo/agent-project/
├── app.jar              -> symlink a bootstrap-0.0.1-SNAPSHOT.jar
├── bootstrap-0.0.1-SNAPSHOT.jar
├── .env                  (chmod 600 — credenciales de DB)
└── data/
    └── inbox/            (progreso.json / nuevo.json, ver docs/SYNC_PROTOCOL.md)

/home/srdejo/agent-project-frontend/
└── ...                   build estático de Angular (contenido de dist/frontend/browser/, sin la carpeta "browser" anidada)
```

`data/` **nunca se borra** en un redeploy — el store real de proyectos está en Postgres, no en archivos.

## Servicio systemd (`agent-project.service`)

```ini
[Unit]
Description=Agent Project Backend
After=network.target postgresql.service

[Service]
Type=simple
User=srdejo
WorkingDirectory=/home/srdejo/agent-project
EnvironmentFile=/home/srdejo/agent-project/.env
ExecStart=/usr/bin/env java -jar /home/srdejo/agent-project/app.jar --server.port=8083
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

`.env` (chmod 600, no versionado):
```
SPRING_PROFILES_ACTIVE=prod
DB_HOST=localhost
DB_PORT=5432
DB_NAME=agent_project
DB_USER=agent_project
DB_PASSWORD=<password-real>
SYNC_INBOX_DIR=/home/srdejo/agent-project/data/inbox
SYNC_POLL_INTERVAL_MS=21600000
```

Instalación (ya hecha, dejar documentado para el próximo redeploy de cero):

```bash
sudo mv agent-project.service /etc/systemd/system/agent-project.service
sudo chmod 600 /home/srdejo/agent-project/.env
sudo systemctl daemon-reload
sudo systemctl enable --now agent-project
```

Flyway corre las migraciones automáticamente al arrancar el jar — no requiere paso manual en cada deploy.

## nginx (`/etc/nginx/sites-enabled/agent`)

```nginx
server {
    listen 80;
    server_name agent.srdejo.com.co;

    root /home/srdejo/agent-project-frontend;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8083/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

(Certbot agregó automáticamente el bloque `listen 443 ssl` + redirect 80→443 al correr `certbot --nginx`.)

## Redeploy

Usar `deploy.ps1` en la raíz del repo (opción `5) Deploy Todo`, o backend/frontend por separado) — sube el jar/build nuevo y reinicia el servicio/nginx. Si el build de Angular usa SSR/prerender, el contenido queda en `dist/frontend/browser/` — copiar ese subdirectorio aplanado, no `dist/frontend/` completo. Ver `docs/SYNC_PROTOCOL.md` para cómo OpenClaw entrega los archivos JSON al inbox una vez el backend está corriendo.

## Backups

`deploy.ps1` opción `8) Backup Database` hace `pg_dump` de `agent_project` y lo descarga a `.\backups\` local. Requiere que `/home/srdejo/backups/` exista en el servidor.
