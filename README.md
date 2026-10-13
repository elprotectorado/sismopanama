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

## APK de Android (opcional)
App Android (TWA) que abre la PWA a pantalla completa. Se compila en GitHub Actions: `.github/workflows/apk.yml`.
1. Sube el repo a GitHub y despliega `worker.js` en Cloudflare.
2. Por defecto usa `https://sismopa.protectorado.workers.dev`. Para otra URL: variable de Actions `SITE_URL` o input al ejecutar el workflow.
3. Actions → Build APK → Run workflow. El APK queda en Releases (`apk-latest`).
4. El resumen del job muestra la huella `TWA_SHA256`. Ponla como variable del Worker `TWA_SHA256` (quita la barra del navegador) y `APK_URL` = enlace de descarga del APK (activa el botón «Descargar APK» y `/app.apk`).
5. Guarda el artifact `llave-firma-guardala` como secret `KEYSTORE_B64` (`base64 -w0 istmo.keystore`) y `KS_PASS`; sin eso cada compilación usa una llave nueva y no se actualiza sobre la anterior.
