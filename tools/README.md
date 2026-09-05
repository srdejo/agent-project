# tools/ — movido a `infra/portfolio/`

La generación y el envío del sync viven ahora en
[`infra/portfolio/`](../../infra/portfolio/README.md), en la raíz del workspace.

```powershell
cd "D:\Workspace Daniel\01-activos\srdejo\infra\portfolio"
.\sync-portfolio.ps1 -DryRun
```

**Por qué se movió**: el pipeline lee los `docs/ROADMAP.md` de **todos** los repos del workspace,
no solo el de este proyecto. Vivía aquí por accidente histórico, igual que `infra/deploy.ps1`
despliega todos los proyectos sin vivir dentro de ninguno.

**Qué cambió, además del lugar**: lo que había aquí subía un `progreso.json` escrito a mano una
sola vez en agosto de 2026. No había generador, así que cada corrida posterior subía el mismo
estado congelado y el backend respondía `UNCHANGED` — parecía que funcionaba y no actualizaba
nada. Ahora el estado se deriva de los roadmaps en cada corrida.

Lo viejo quedó en `_obsoleto/` como referencia; no lo corras, sube datos de agosto.

El contrato que espera el backend sigue documentado en
[`docs/SYNC_PROTOCOL.md`](../docs/SYNC_PROTOCOL.md) — este cambio no lo toca.
