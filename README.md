# ExpiryControl — Backend

API REST de ExpiryControl, una aplicación para registrar productos con su cantidad, precio y fecha de vencimiento, consultar cuáles están vencidos, por vencer o vigentes, y llevar el registro de las pérdidas económicas por productos vencidos. Cada usuario ve y administra únicamente sus propios productos.

El frontend (Expo / React Native) está en un repositorio aparte: [expirycontrol-frontend](https://github.com/cristopherdiaz7/expirycontrol-frontend).

## Contenido

- [Stack](#stack)
- [Requisitos](#requisitos)
- [Instalación](#instalación)
- [Variables de entorno](#variables-de-entorno)
- [Perfiles](#perfiles)
- [Autenticación](#autenticación)
- [Endpoints](#endpoints)
- [Ejemplos](#ejemplos)
- [Reglas de vencimiento](#reglas-de-vencimiento)
- [Notificaciones](#notificaciones)
- [Precios y pérdidas](#precios-y-pérdidas)
- [Errores](#errores)
- [CORS](#cors)
- [Tests](#tests)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Problemas frecuentes](#problemas-frecuentes)

## Stack

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.0.6 (Web MVC, Data JPA, Validation, Security) |
| Base de datos | PostgreSQL 16 (en Docker) |
| Autenticación | JWT firmado con HS256 (JJWT 0.12.5), contraseñas con BCrypt |
| Build | Maven (incluye wrapper, no hace falta instalarlo) |
| Tests | JUnit 5, Mockito, MockMvc, H2 en memoria |

## Requisitos

- **Java 21** (JDK).
- **Docker** con Docker Compose, para la base de datos.
- Git.

No hace falta instalar Maven ni PostgreSQL.

## Instalación

### 1. Clonar el repositorio

```bash
git clone https://github.com/cristopherdiaz7/expirycontrol-backend.git
cd expirycontrol-backend
```

### 2. Crear el archivo `.env`

El proyecto no incluye credenciales. Se configuran en un archivo `.env` en la raíz, que Git ignora.

```bash
# Linux / macOS / Git Bash
cp .env.example .env
```

```powershell
# Windows PowerShell
Copy-Item .env.example .env
```

Editar `.env` y completar:

- `POSTGRES_PASSWORD`: una contraseña a elección para la base.
- `JWT_SECRET`: una clave aleatoria de **al menos 32 caracteres**.

Para generar la clave:

```bash
# Linux / macOS / Git Bash
openssl rand -hex 48
```

```powershell
# Windows PowerShell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | ForEach-Object { $_.ToString('x2') })
```

> **Nunca subas el archivo `.env` al repositorio.** Antes de cada commit revisá `git status`: `.env` no debe aparecer.

### 3. Levantar PostgreSQL

```bash
docker compose up -d
```

Crea el contenedor `expiry-control-postgres` con la base `expiry_control`, accesible en el puerto **5433** de la PC. Los datos se guardan en un volumen de Docker y sobreviven a los reinicios.

> La contraseña de `.env` se aplica **solo la primera vez**, cuando se crea el volumen. Si la cambiás después, ver [Problemas frecuentes](#problemas-frecuentes).

### 4. Ejecutar el backend

```bash
# Linux / macOS / Git Bash
./mvnw spring-boot:run
```

```powershell
# Windows PowerShell
.\mvnw.cmd spring-boot:run
```

La API queda disponible en `http://localhost:8080` cuando aparece la línea `Started DemoApplication`. Las tablas se crean solas en el primer arranque.

### 5. Comprobar que funciona

```bash
curl http://localhost:8080/products
```

Respuesta esperada (todavía no hay sesión iniciada):

```json
{"error":"Autenticación requerida"}
```

## Variables de entorno

Se leen del archivo `.env` o del entorno del sistema.

| Variable | Obligatoria | Valor por defecto | Uso |
|---|---|---|---|
| `POSTGRES_USER` | Sí | — | Usuario de PostgreSQL (backend y Docker) |
| `POSTGRES_PASSWORD` | Sí | — | Contraseña de PostgreSQL (backend y Docker) |
| `JWT_SECRET` | Sí | — | Clave para firmar los tokens; mínimo 32 caracteres |
| `POSTGRES_DB` | No | `expiry_control` | Nombre de la base que crea Docker |
| `DB_URL` | Solo en `prod` | `jdbc:postgresql://localhost:5433/expiry_control?options=-c%20TimeZone%3DUTC` | URL JDBC de la base |
| `JWT_EXPIRATION_MS` | No | `3600000` (1 hora) | Duración del token en milisegundos |
| `CORS_ALLOWED_ORIGINS` | Solo en `prod` | Los cuatro orígenes locales de Expo Web | Orígenes permitidos, separados por coma |
| `SPRING_PROFILES_ACTIVE` | No | `dev` | Perfil activo. Se define en el entorno del sistema, **no** en `.env` |

Si falta una variable obligatoria, la aplicación no arranca.

## Perfiles

| | `dev` (por defecto) | `prod` |
|---|---|---|
| Cómo se activa | No requiere nada | `SPRING_PROFILES_ACTIVE=prod` |
| Tablas | Se crean y actualizan solas (`ddl-auto: update`) | Solo se verifica el esquema (`validate`); no se modifica |
| SQL en consola | Sí | No |
| `DB_URL` | Opcional | Obligatoria |
| `CORS_ALLOWED_ORIGINS` | Opcional | Obligatoria |
| Detalle de errores internos | Por defecto de Spring | Oculto |

Existe además el perfil `test`, que usan los tests automáticos con una base H2 en memoria.

## Autenticación

1. **Registro:** `POST /register` crea el usuario. La contraseña se guarda hasheada con BCrypt y nunca se devuelve.
2. **Login:** `POST /login` devuelve un token JWT y los datos del usuario.
3. **Uso del token:** todas las demás rutas requieren la cabecera:

   ```
   Authorization: Bearer <token>
   ```

4. **Duración:** el token vence a la hora (configurable con `JWT_EXPIRATION_MS`). No hay renovación automática: al vencer hay que iniciar sesión de nuevo.

El token contiene el email (`sub`), el id (`userId`), el nombre (`name`) y las fechas de emisión y vencimiento.

Cuando la autenticación falla, la respuesta es `401 Unauthorized` con uno de estos mensajes:

| Situación | Respuesta |
|---|---|
| No se envió token | `{"error":"Autenticación requerida"}` |
| Token mal formado, con firma incorrecta o de un usuario que ya no existe | `{"error":"Token inválido"}` |
| Token vencido | `{"error":"Token expirado"}` |
| Email o contraseña incorrectos en el login | `{"error":"Email o contraseña incorrectos"}` |

## Endpoints

Rutas públicas: `/register` y `/login`. Todas las demás requieren token.

### Usuarios

| Método | Ruta | Auth | Cuerpo | Respuesta |
|---|---|---|---|---|
| POST | `/register` | No | `name` y `email` (hasta 255 caracteres), `password` (de 6 a 72 caracteres) | `201` con `id`, `name`, `email` · `400` datos inválidos · `409` email ya registrado |
| POST | `/login` | No | `email`, `password` | `200` con `token` y `user` · `400` datos inválidos · `401` credenciales incorrectas |

### Productos

| Método | Ruta | Parámetros | Respuesta |
|---|---|---|---|
| POST | `/products` | Cuerpo: producto | `201` con el producto creado · `400` |
| GET | `/products` | — | `200` con la lista del usuario, en orden de alta |
| GET | `/products/{id}` | — | `200` con el producto · `404` |
| PUT | `/products/{id}` | Cuerpo: producto | `200` con el producto actualizado · `400` · `404` |
| DELETE | `/products/{id}` | `today` (opcional) | `204` sin cuerpo · `404` |
| GET | `/products/expired` | `today` (opcional) | `200` con los productos vencidos |
| GET | `/products/expiring` | `days` (opcional, por defecto 7), `today` (opcional) | `200` con los productos por vencer |
| GET | `/products/stats` | `days` (opcional, por defecto 7), `today` (opcional) | `200` con los cuatro contadores |

Todas pueden responder además `401` si falta el token o no es válido.

Un producto ajeno se trata igual que uno inexistente: responde `404`.

### Notificaciones

| Método | Ruta | Parámetros | Respuesta |
|---|---|---|---|
| GET | `/notifications` | `today` (opcional) | `200` con `unreadCount` y la lista de notificaciones |
| POST | `/notifications/{productId}/read` | `today` (opcional) | `200` con la lista actualizada · `404` si el producto no existe, es de otro usuario o no tiene notificación |
| POST | `/notifications/read-all` | `today` (opcional) | `200` con la lista actualizada |

Ver [Notificaciones](#notificaciones) para las categorías y el estado de lectura.

### Pérdidas

| Método | Ruta | Parámetros | Respuesta |
|---|---|---|---|
| GET | `/losses` | `today` (opcional) | `200` con la lista de pérdidas, de la más reciente a la más antigua |
| GET | `/losses/stats` | `today` (opcional) | `200` con el importe total, la cantidad de pérdidas, las unidades perdidas y el detalle por mes |

Ver [Precios y pérdidas](#precios-y-pérdidas) para las reglas.

### Campos de un producto

| Campo | Tipo | Regla |
|---|---|---|
| `name` | texto | Obligatorio, hasta 255 caracteres |
| `description` | texto | Obligatorio, hasta 255 caracteres |
| `category` | texto | Obligatorio, hasta 255 caracteres |
| `quantity` | entero | Obligatorio, entre 0 y 1.000.000 |
| `expirationDate` | fecha `AAAA-MM-DD` | Obligatorio |
| `unitPrice` | decimal | Obligatorio, 0 o mayor, hasta 10 dígitos enteros y 2 decimales. Precio unitario en pesos argentinos (ARS) |

La respuesta incluye además el `id`. En los productos creados antes de que existiera el precio, `unitPrice` es `null` hasta que se editen.

## Ejemplos

Los ejemplos usan `curl`. En Windows PowerShell, usar `curl.exe` en lugar de `curl`.

### Registro

```bash
curl -X POST http://localhost:8080/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Ana","email":"ana@example.com","password":"secreta123"}'
```

```json
{"id":1,"name":"Ana","email":"ana@example.com"}
```

### Login

```bash
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","password":"secreta123"}'
```

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": { "id": 1, "name": "Ana", "email": "ana@example.com" }
}
```

En los ejemplos siguientes, `$TOKEN` es el valor de `token`.

### Crear un producto

```bash
curl -X POST http://localhost:8080/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Leche","description":"Entera","category":"Bebidas","quantity":12,"expirationDate":"2030-01-15","unitPrice":1250.50}'
```

```json
{"id":1,"name":"Leche","description":"Entera","category":"Bebidas","quantity":12,"expirationDate":"2030-01-15","unitPrice":1250.50}
```

> La API acepta tildes y eñes (UTF-8). El ejemplo no las usa porque algunas terminales de Windows las envían con otra codificación y el servidor responde `400`.

### Listar productos

```bash
curl http://localhost:8080/products -H "Authorization: Bearer $TOKEN"
```

### Productos por vencer en 14 días

```bash
curl "http://localhost:8080/products/expiring?days=14&today=2026-10-08" \
  -H "Authorization: Bearer $TOKEN"
```

### Estadísticas

```bash
curl "http://localhost:8080/products/stats?days=7&today=2026-10-08" \
  -H "Authorization: Bearer $TOKEN"
```

```json
{"totalProducts":5,"expiredProducts":1,"expiringSoonProducts":2,"validProducts":2}
```

### Eliminar un producto

```bash
curl -X DELETE http://localhost:8080/products/1 -H "Authorization: Bearer $TOKEN"
```

Responde `204` sin cuerpo.

## Reglas de vencimiento

Con `hoy` como fecha de referencia y `days` como cantidad de días del filtro:

| Estado | Regla |
|---|---|
| Vencido | `expirationDate` es hoy o anterior |
| Por vencer | `expirationDate` es posterior a hoy y cae dentro de los próximos `days` días (el último día está incluido) |
| Vigente | `expirationDate` es posterior a hoy + `days` |

Un producto que vence hoy ya cuenta como vencido.

En las estadísticas, `validProducts` es el total menos los vencidos y los por vencer.

### El parámetro `today`

El servidor trabaja en UTC. Sin más datos, su "hoy" puede no coincidir con el del usuario: en Argentina (UTC-3), entre las 21:00 y las 24:00 el servidor ya está en el día siguiente.

Para evitarlo, el cliente puede enviar su fecha local:

```
GET /products/stats?days=7&today=2026-10-08
```

- Si se envía, esa fecha se usa como "hoy".
- Si no se envía, se usa la fecha UTC del servidor.
- Solo se acepta una fecha que difiera **como máximo un día** de la del servidor. Cualquier otra responde `400`.

### El parámetro `days`

Por defecto vale 7. Un valor negativo se trata como 0.

## Notificaciones

Las notificaciones no se guardan: se calculan en cada consulta a partir de los productos del usuario. Cada producto aparece **como máximo en una categoría**, según los días que faltan para su vencimiento:

| Categoría (`category`) | Días restantes | Gravedad (`severity`) |
|---|---|---|
| `EXPIRED` | Menos de 0 (ya venció) | `EXPIRED` |
| `EXPIRES_TODAY` | 0 | `EXPIRED` |
| `WITHIN_3_DAYS` | 1 a 3 | `UPCOMING` |
| `WITHIN_7_DAYS` | 4 a 7 | `UPCOMING` |
| `WITHIN_14_DAYS` | 8 a 14 | `UPCOMING` |

Un producto que vence en más de 14 días no genera notificación.

Ejemplo de respuesta de `GET /notifications`, ordenada de más urgente a menos urgente:

```json
{
  "unreadCount": 1,
  "notifications": [
    {
      "productId": 4,
      "productName": "Leche",
      "category": "EXPIRED",
      "severity": "EXPIRED",
      "expirationDate": "2026-10-07",
      "daysRemaining": -2,
      "read": true
    },
    {
      "productId": 9,
      "productName": "Yogur",
      "category": "WITHIN_3_DAYS",
      "severity": "UPCOMING",
      "expirationDate": "2026-10-11",
      "daysRemaining": 2,
      "read": false
    }
  ]
}
```

### Estado de lectura

- Se guarda por usuario, en la base de datos.
- Al marcar una notificación como leída se registra la categoría y la fecha de vencimiento de ese momento.
- Si el producto **cambia de categoría** (por ejemplo, de `WITHIN_7_DAYS` a `WITHIN_3_DAYS`) o se le **modifica la fecha**, la notificación vuelve a figurar sin leer.
- Marcar dos veces la misma notificación no duplica registros.
- Al eliminar un producto desaparecen su notificación y su estado de lectura.

### Diferencia con los endpoints de vencimiento

El centro de notificaciones distingue "vence hoy" (`EXPIRES_TODAY`) de "vencido" (`EXPIRED`). Los endpoints `/products/expired` y `/products/stats` no cambian: para ellos, un producto que vence hoy sigue contando como vencido.

Las notificaciones aceptan el parámetro `today`, con las mismas reglas que el resto.

## Precios y pérdidas

### Precio unitario

Cada producto tiene un precio unitario (`unitPrice`) en pesos argentinos (ARS). Es obligatorio al crear y al editar, y se guarda como decimal exacto con dos decimales.

### Cuándo se registra una pérdida

Un producto genera una pérdida cuando está **vencido** (su fecha es hoy o anterior, la misma regla que usa el resto del sistema) y **tiene precio**. Los productos sin precio no generan pérdidas.

Cada pérdida guarda una copia de los datos del producto en ese momento:

| Campo | Contenido |
|---|---|
| `productName` | Nombre del producto |
| `quantity` | Cantidad afectada |
| `unitPrice` | Precio unitario |
| `expirationDate` | Fecha de vencimiento, que es la fecha de la pérdida |
| `totalAmount` | Importe total: cantidad × precio unitario |
| `productId` | Id del producto, o `null` si ya fue eliminado |
| `productDeleted` | `true` si el producto ya fue eliminado |

Las pérdidas se registran al consultarlas (`GET /losses` o `GET /losses/stats`) y al eliminar un producto vencido. No hace falta ninguna acción del usuario.

### Reglas

- **Una sola pérdida por producto.** No se cuenta dos veces, aunque lleguen consultas simultáneas; la base de datos lo garantiza con una restricción de unicidad.
- **Mientras el producto existe, la pérdida lo acompaña.** Si se corrige su nombre, cantidad o precio, la pérdida se actualiza. Si la fecha pasa a ser futura, la pérdida se quita.
- **Al eliminar el producto, la pérdida queda en el historial** tal como estaba, y ya no se modifica.
- Cada usuario ve únicamente sus pérdidas.

### Ejemplos

`GET /losses`:

```json
[
  {
    "id": 7,
    "productId": 12,
    "productName": "Yogur",
    "quantity": 4,
    "unitPrice": 800.00,
    "expirationDate": "2026-10-09",
    "totalAmount": 3200.00,
    "productDeleted": false
  },
  {
    "id": 5,
    "productId": null,
    "productName": "Leche",
    "quantity": 3,
    "unitPrice": 1250.50,
    "expirationDate": "2026-10-07",
    "totalAmount": 3751.50,
    "productDeleted": true
  }
]
```

`GET /losses/stats`:

```json
{
  "currency": "ARS",
  "totalAmount": 10951.50,
  "lossCount": 3,
  "unitsLost": 8,
  "byMonth": [
    { "month": "2026-10", "totalAmount": 6951.50, "lossCount": 2, "unitsLost": 7 },
    { "month": "2026-08", "totalAmount": 4000.00, "lossCount": 1, "unitsLost": 1 }
  ]
}
```

El detalle por mes se agrupa por la fecha de vencimiento y va del mes más reciente al más antiguo.

Ambos endpoints, y también `DELETE /products/{id}`, aceptan el parámetro `today` para decidir con la fecha local del usuario si un producto ya venció.

## Errores

Todos los errores devuelven JSON con la clave `error`. Los de validación agregan `details`, con un mensaje por campo.

| Código | Cuándo | Ejemplo |
|---|---|---|
| 400 | Campos inválidos o faltantes | `{"error":"Datos de entrada inválidos o faltantes","details":{"email":"El email debe ser válido"}}` |
| 400 | El cuerpo no es JSON válido | `{"error":"El cuerpo de la petición no es un JSON válido"}` |
| 400 | Un parámetro tiene un tipo incorrecto (`days=abc`, `today=08-10-2026`) | `{"error":"Valor inválido para el parámetro 'days'"}` |
| 400 | `today` difiere más de un día de la fecha del servidor | `{"error":"La fecha enviada no coincide con la fecha actual"}` |
| 401 | Falta autenticación o es inválida | Ver [Autenticación](#autenticación) |
| 404 | El producto no existe o es de otro usuario | `{"error":"Producto no encontrado"}` |
| 404 | Se intenta marcar como leída la notificación de un producto que no tiene | `{"error":"El producto no tiene notificaciones"}` |
| 409 | El email ya está registrado | `{"error":"El email ya se encuentra registrado"}` |

## CORS

CORS solo afecta a los navegadores. Una app nativa (Android o iOS) no pasa por esta restricción.

Por defecto, en el perfil `dev` se permiten los orígenes locales de Expo Web:

- `http://localhost:8081` y `http://127.0.0.1:8081`
- `http://localhost:19006` y `http://127.0.0.1:19006`

Para permitir otros, definir `CORS_ALLOWED_ORIGINS` en `.env` con la lista completa separada por comas:

```
CORS_ALLOWED_ORIGINS=http://localhost:8081,https://mi-app.example.com
```

Métodos permitidos: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`. Cabeceras permitidas: `Authorization` y `Content-Type`.

## Tests

```bash
# Linux / macOS / Git Bash
./mvnw test
```

```powershell
# Windows PowerShell
.\mvnw.cmd test
```

Los tests usan una base H2 en memoria: **no necesitan Docker ni el archivo `.env`**, y no tocan los datos de PostgreSQL.

| Clase | Tests | Qué cubre |
|---|---|---|
| `FullFlowIntegrationTest` | 9 | Recorrido completo de un usuario, aislamiento entre dos usuarios, casos que cruzan funcionalidades y entradas fuera de los límites |
| `SecurityIntegrationTest` | 22 | Respuestas 401, acceso con token válido, registro, login, CORS, parámetro `today` y aislamiento entre usuarios |
| `ProductApiIntegrationTest` | 11 | CRUD de productos por HTTP y validación de campos |
| `LossApiIntegrationTest` | 18 | Validación del precio, registro de pérdidas, importes, no duplicación, correcciones, historial al eliminar, estadísticas y aislamiento entre usuarios |
| `NotificationApiIntegrationTest` | 11 | Notificaciones por HTTP: listado, lectura, cambio de categoría, borrado y aislamiento entre usuarios |
| `NotificationServiceTest` | 11 | Categorías sin duplicados, límites de días y estado de lectura |
| `ProductServiceTest` | 11 | Reglas de vencimiento, estadísticas y casos límite de fechas |
| `JwtServiceTest` | 8 | Generación y validación de tokens |
| `UserServiceTest` | 6 | Registro y login |
| `DemoApplicationTests` | 1 | Arranque de la aplicación |

Total: 108 tests.

El resultado de la validación de conjunto, incluido el recorrido en la app web, está en [VALIDACION.md](VALIDACION.md).

## Estructura del proyecto

```
expirycontrol-backend/
├── docker-compose.yml          PostgreSQL 16
├── .env.example                Plantilla de variables de entorno
├── pom.xml                     Dependencias y build
└── src/
    ├── main/
    │   ├── java/com/example/demo/
    │   │   ├── DemoApplication.java   Punto de entrada (fija UTC y define BCrypt)
    │   │   ├── config/                Seguridad y CORS
    │   │   ├── controller/            Endpoints HTTP
    │   │   ├── dto/                   Datos de entrada y salida
    │   │   ├── exception/             Excepciones y respuestas de error
    │   │   ├── model/                 Entidades User, Product, NotificationRead y Loss
    │   │   ├── repository/            Acceso a datos (Spring Data JPA)
    │   │   ├── security/              Filtro JWT, servicio JWT y respuesta 401
    │   │   └── service/               Lógica de negocio
    │   └── resources/
    │       ├── application.yaml       Configuración común
    │       ├── application-dev.yaml   Perfil de desarrollo
    │       └── application-prod.yaml  Perfil de producción
    └── test/
        ├── java/com/example/demo/     Tests
        └── resources/
            └── application-test.yaml  Perfil de tests (H2)
```

### Base de datos

| Tabla | Columnas | Restricciones |
|---|---|---|
| `users` | `id`, `name`, `email`, `password` | `email` único |
| `products` | `id`, `name`, `description`, `category`, `quantity`, `expiration_date`, `unit_price`, `user_id` | `user_id` referencia a `users(id)` |
| `notification_reads` | `id`, `user_id`, `product_id`, `category`, `expiration_date`, `read_at` | Una fila por usuario y producto; se borra junto con el producto |
| `losses` | `id`, `user_id`, `product_id`, `product_name`, `quantity`, `unit_price`, `expiration_date`, `total_amount`, `recorded_at` | `product_id` único; pasa a `null` cuando se elimina el producto |

## Problemas frecuentes

**`Could not resolve placeholder 'JWT_SECRET'` al arrancar**

Falta el archivo `.env` o alguna de sus variables. Crearlo a partir de `.env.example` (paso 2 de la instalación).

**`JWT_SECRET debe tener al menos 32 caracteres`**

La clave es demasiado corta. Generar una nueva como se indica en la instalación.

**`password authentication failed for user "expiry_user"`**

La contraseña de `.env` no coincide con la que tiene la base. Pasa si se cambió `POSTGRES_PASSWORD` después de crear el contenedor, o si se perdió el `.env`. Para fijar en la base la contraseña que está en `.env` sin perder datos:

```bash
docker exec expiry-control-postgres psql -U expiry_user -d expiry_control -c "ALTER USER expiry_user WITH PASSWORD 'la-contraseña-de-tu-env'"
```

**`Connection refused` al conectar con la base**

El contenedor no está corriendo. Ejecutar `docker compose up -d` y verificar con `docker compose ps`.

**`GET /losses` falla con un error interno en una base creada antes de la validación final**

La columna del importe quedó con el tamaño anterior. Ampliarla una vez:

```bash
docker exec expiry-control-postgres psql -U expiry_user -d expiry_control -c "ALTER TABLE losses ALTER COLUMN total_amount TYPE numeric(19,2)"
```

**El puerto 8080 está en uso**

Ya hay otra instancia del backend corriendo. Cerrarla antes de iniciar una nueva.

**Después de cambiar `JWT_SECRET`, la app pide iniciar sesión otra vez**

Es el comportamiento esperado: los tokens emitidos con la clave anterior dejan de ser válidos.
