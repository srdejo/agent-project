# DEPLOYMENT.md

Cómo desplegar `agent-project` en `nolost-vps`. Esta guía es documentación para aplicar manualmente (o en una sesión futura con acceso SSH al servidor) — no fue ejecutada en la sesión donde se escribió, que no tiene acceso al VPS.

## Prerrequisitos en el VPS

- PostgreSQL disponible (contenedor propio o instalación nativa — decidir si se reutiliza la instancia existente para `nolost` con una base de datos/usuario separado, o si se levanta una instancia nueva; no hay evidencia en este repo de cuál aplica en `nolost-vps` hoy).
- Base de datos y usuario creados:
  ```sql
  CREATE DATABASE agent_project;
  CREATE USER agent_project WITH PASSWORD '<password-real>';
  GRANT ALL PRIVILEGES ON DATABASE agent_project TO agent_project;
  ```
- Java 21 instalado (para correr el jar).
- nginx ya instalado y en uso (mismo que sirve `nolost`).

## Estructura de directorios esperada

```
/home/srdejo/agent-project/
├── app.jar              -> symlink al jar desplegado (ver deploy.ps1)
├── agent-project-backend-0.0.1-SNAPSHOT.jar
└── data/
    └── inbox/
        ├── processed/    (creados automáticamente por el backend)
        └── rejected/

/home/srdejo/agent-project-frontend/
└── ...                   (build estático de Angular)
```

`data/` **nunca se borra** en un redeploy — es donde vive el estado local del inbox (el store real de proyectos está en Postgres, no en archivos).

## Servicio systemd (`agent-project.service`)

```ini
[Unit]
Description=Agent Project Backend
After=network.target postgresql.service

[Service]
Type=simple
User=srdejo
WorkingDirectory=/home/srdejo/agent-project
ExecStart=/usr/bin/env java -jar /home/srdejo/agent-project/app.jar
Restart=on-failure
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=DB_HOST=localhost
Environment=DB_PORT=5432
Environment=DB_NAME=agent_project
Environment=DB_USER=agent_project
Environment=DB_PASSWORD=<password-real>
Environment=SYNC_INBOX_DIR=/home/srdejo/agent-project/data/inbox
Environment=SYNC_POLL_INTERVAL_MS=300000

[Install]
WantedBy=multi-user.target
```

Instalación (una sola vez):

```bash
sudo mv agent-project.service /etc/systemd/system/agent-project.service
sudo systemctl daemon-reload
sudo systemctl enable agent-project
```

Flyway corre las migraciones automáticamente al arrancar el jar — no requiere paso manual en cada deploy.

## nginx

Sirve el frontend estático y hace proxy de `/api` al backend (puerto 8080, solo loopback):

```nginx
server {
    listen 80;
    server_name agent-project.<tu-dominio>;  # o el host que corresponda

    root /home/srdejo/agent-project-frontend;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

## Deploy

Usar `deploy.ps1` en la raíz del repo (opción `5) Deploy Todo`, o backend/frontend por separado). Ver `docs/SYNC_PROTOCOL.md` para cómo OpenClaw entrega los archivos JSON al inbox una vez el backend está corriendo.

## Backups

`deploy.ps1` opción `8) Backup Database` hace `pg_dump` de `agent_project` y lo descarga a `.\backups\` local. Requiere que `/home/srdejo/backups/` exista en el servidor.
