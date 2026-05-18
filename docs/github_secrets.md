# GitHub Secrets — EternaMente

Guía de configuración de credenciales seguras para GitHub Actions.

**Regla principal:** Ninguna contraseña, keystore, ni archivo de credenciales se sube al repositorio.  
Todos los valores sensibles viven exclusivamente en GitHub Secrets.

---

## Ruta para crear secrets

```
GitHub → Repositorio → Settings → Secrets and variables → Actions → New repository secret
```

---

## Secrets actuales (CI debug — ya funcionando)

El workflow de debug **no requiere secrets**. El `google-services.json` real está en `.gitignore` y en CI se genera una versión falsa automáticamente solo para compilar.

---

## Secrets necesarios para activar el release build

Estos secrets deben crearse **antes** de descomentar el job `build-release` en `.github/workflows/android_ci.yml`.

| Secret | Para qué sirve | Obligatorio para release |
|---|---|---|
| `KEYSTORE_BASE64` | Archivo `.jks` del keystore de release, codificado en Base64 | Sí |
| `KEYSTORE_PASSWORD` | Contraseña del archivo `.jks` | Sí |
| `KEY_ALIAS` | Alias de la clave dentro del keystore | Sí |
| `KEY_PASSWORD` | Contraseña de la clave (puede ser igual a `KEYSTORE_PASSWORD`) | Sí |
| `GOOGLE_SERVICES_JSON_BASE64` | `google-services.json` real codificado en Base64 (para builds release con Firebase real) | Sí |
| `FIREBASE_APP_ID` | App ID del proyecto en Firebase Console (formato: `1:XXXXXX:android:YYYYYY`) | Solo para Firebase App Distribution |
| `FIREBASE_TOKEN` | Token de Firebase CLI para subir builds a App Distribution | Solo para Firebase App Distribution |

---

## Cómo crear el keystore de release (una sola vez)

> Guarda el archivo `.jks` y las contraseñas en un gestor de contraseñas seguro.  
> **Nunca** subas el `.jks` al repositorio — está protegido por `.gitignore`.

```bash
# Ejecutar en local, fuera del directorio del proyecto si se prefiere
keytool -genkey -v \
  -keystore eternamente-release.jks \
  -alias eternamente \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Responde las preguntas interactivas y guarda las contraseñas.

---

## Cómo codificar el keystore en Base64 para GitHub Secrets

```bash
# macOS / Linux
base64 -i eternamente-release.jks | tr -d '\n'

# El resultado (una línea larga) es el valor del secret KEYSTORE_BASE64
```

---

## Cómo codificar google-services.json en Base64

```bash
base64 -i app/google-services.json | tr -d '\n'
# El resultado es el valor de GOOGLE_SERVICES_JSON_BASE64
```

---

## Cómo activar el release build en CI

1. Crear todos los secrets de la tabla anterior en GitHub.
2. Abrir `.github/workflows/android_ci.yml`.
3. Descomentar el bloque `# build-release:` (eliminar los `#` de cada línea).
4. Hacer commit y push a `main`.

El job `build-release` solo corre en push a `main` y solo si `build-and-test` (debug) pasa primero.

---

## Archivos protegidos por `.gitignore`

Los siguientes patrones están en `.gitignore` y **nunca** deben subirse al repositorio:

```
*.jks
*.keystore
*.p12
*.pem
*.pfx
google-services.json
local.properties
keystore.properties
service-account*.json
firebase-adminsdk*.json
```

---

## Verificar que no hay credenciales expuestas

```bash
# Verificar que ningún archivo sensible está trackeado por git
git ls-files | grep -E "\.(jks|keystore|p12|pem)$"
git ls-files | grep -E "(google-services|service-account|firebase-adminsdk)"

# Si algún comando devuelve resultados, ese archivo está en el repo — eliminarlo con:
# git rm --cached <archivo>
```

---

## Estado actual del CI

| Job | Estado | Requiere secrets |
|---|---|---|
| `build-and-test` (debug) | Activo | No |
| `build-release` (firmado) | Comentado — listo para activar | Sí |

El CI de debug siempre funciona sin ninguna configuración adicional.
