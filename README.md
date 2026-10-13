# ISTMO · Sismo Panamá
Detector sísmico en un único Cloudflare Worker (IGC-UP + USGS + EMSC + GEOFON + aviso PTWC), PWA instalable en cualquier dispositivo, alertas, réplicas y API. Todo con fuentes abiertas: no requiere ninguna clave de API.
By @elprotectorado

## Estructura
- `src/` código fuente por partes (backend, shared, app-1…5, css, html, sw, docs)
- `build.mjs` ensambla todo en `worker.js` (un solo archivo)
- `worker.js` resultado listo para pegar en el editor en línea de Cloudflare
- `tools/geo.mjs` regenera `src/app-0-geo.js` (mapa vectorial Natural Earth, dominio público)
- `test/` pruebas del backend y humo del cliente

## Uso
    node build.mjs      # regenera worker.js
Pega `worker.js` en el editor de Cloudflare. Push opcional: KV `SISMO_KV` + Cron `* * * * *`.

## Licencia y datos
Código bajo licencia MIT (ver `LICENSE`). Los datos sísmicos pertenecen a sus fuentes (IGC-UP, USGS, EMSC) y se rigen por sus propios términos de uso.

## APK de Android (automático)
App Android (TWA = Trusted Web Activity: abre la PWA en pantalla completa con Chrome, sin barra de direcciones).
- `.github/workflows/apk.yml` compila `android/` en cada push a `main` y publica `sismopty.apk` en Releases (`apk-latest`).
- La llave `android/istmo.keystore` y su huella `android/fingerprint.txt` van en el repo; `build.mjs` incrusta la huella en `/.well-known/assetlinks.json`. No hay variables ni secretos que configurar.
- Descarga: `/app.apk` redirige al último APK (botón en el asistente de instalación en Android).

### Qué hace la app Android
- Pantalla de arranque que pide una sola vez notificaciones, ubicación y exención de optimización de batería.
- `AlertWorker` (WorkManager): cada 15 min revisa `/api/quakes` aunque la app esté cerrada y avisa con la misma lógica de niveles de la web. Una petición pequeña por ciclo.

- Web: pantalla de carga, arranque a prueba de fallos con pantalla «Reparar y reiniciar», modo ahorro de batería (sondeo cada 30 s, pausa en segundo plano, pantalla encendida desactivada por defecto).
