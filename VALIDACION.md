# Validación final — notificaciones, precios y pérdidas

Registro de la verificación de conjunto realizada el 9 de octubre de 2026, después de incorporar el centro de notificaciones, el precio unitario y el registro de pérdidas.

## Resultado

| Qué se ejecutó | Resultado |
|---|---|
| Tests automáticos del backend | 108 de 108 |
| Tests automáticos del frontend | 67 de 67 |
| Recorrido completo en la app web, contra el backend y PostgreSQL reales | 31 de 31 comprobaciones |

Durante la validación aparecieron cuatro errores. Los cuatro se corrigieron en esta misma etapa y tienen un test que evita que vuelvan.

## Errores encontrados y corregidos

| Error | Cómo se manifestaba | Corrección |
|---|---|---|
| Importe de pérdida fuera de rango | Un producto con cantidad y precio muy grandes hacía fallar `GET /losses` con error interno, y esa pantalla quedaba rota para el usuario | La columna del importe admite hasta 17 dígitos enteros y la cantidad queda limitada a 1.000.000 |
| Textos demasiado largos | Un nombre, descripción o categoría de más de 255 caracteres producía un error interno en lugar de un mensaje | Validación de longitud: responde `400` indicando el campo |
| Registro con datos demasiado largos | Un nombre de más de 255 caracteres respondía "El email ya se encuentra registrado", que era falso; una contraseña larga producía un error interno | Validación de longitud de nombre, email y contraseña (72 caracteres y 72 bytes, el límite de BCrypt) |
| Orden inestable de los productos | Después de editar un producto, este pasaba al final de la lista | Las listas se devuelven siempre en orden de alta |

Las tres primeras solo aparecían con datos fuera de lo normal. La cuarta se veía en el uso habitual.

## Qué se verificó

### Tests automáticos nuevos (`FullFlowIntegrationTest`)

Todos pasan por HTTP, empezando por el registro y el login reales:

- **Recorrido completo de un usuario:** registro, login, alta de productos con precio, listado, vencidos, por vencer, estadísticas, notificaciones, marcar una y todas como leídas, pérdidas, edición (cambio de categoría de la notificación y actualización de la pérdida), eliminación y segundo login.
- **Aislamiento entre dos usuarios:** productos, estadísticas, vencidos, notificaciones, estado de lectura y pérdidas. Incluye los intentos de leer, modificar, eliminar y marcar como leído un producto ajeno, que responden `404`.
- **Producto que vence hoy:** cuenta como vencido, genera la notificación "vence hoy" y genera pérdida.
- **Cantidad 0 o precio 0:** la pérdida se registra con importe 0.
- **Producto con los valores máximos permitidos:** no rompe las pérdidas.
- **Orden de la lista después de editar.**
- **Entradas fuera de los límites:** siempre `400` con JSON, nunca un error interno.
- **Login con una contraseña de 200 caracteres:** responde `401` normal.

Los casos "producto sin precio: notificación sí, pérdida no" y "eliminar un producto: se van sus notificaciones y queda su pérdida" ya estaban cubiertos por `LossApiIntegrationTest` y `NotificationApiIntegrationTest`, y se repiten dentro del recorrido completo.

### Recorrido en la app web

Se manejó la interfaz real (clics y escritura en los formularios) en un navegador, con el frontend compilado y el backend conectado a PostgreSQL:

1. Validación del login vacío y del registro con contraseñas distintas.
2. Registro, login con contraseña incorrecta y login correcto.
3. La cuenta nueva empieza sin productos.
4. El precio es obligatorio en el formulario.
5. Alta de un producto vencido ayer y de otro que vence en 5 días.
6. Precios con formato argentino en las tarjetas (`$ 1.250,50`; `8.500` leído como ocho mil quinientos).
7. Secciones Vencidos y Por vencer, y filtro de 3 días.
8. Notificaciones: dos sin leer, marcar una, marcar todas.
9. Pérdidas: importe igual a cantidad × precio; el producto no vencido no figura.
10. Editar la cantidad: la pérdida se actualiza y el producto conserva su lugar en la lista.
11. Eliminar el producto: desaparece su notificación y su pérdida queda marcada como "Producto eliminado".
12. Cierre de sesión.

## Qué no se verificó

- **Android e iOS.** Todo el recorrido se hizo en el navegador, en ancho de PC.
- **Sesión vencida por tiempo** (esperar la hora de duración del token). El comportamiento ante un `401` está cubierto por tests.
- **Importes cercanos al máximo permitido en la pantalla.** El backend los calcula con decimales exactos; el navegador puede mostrarlos redondeados a partir de unos 9 mil billones de pesos.

## Nota para instalaciones anteriores a esta etapa

Hibernate no modifica columnas que ya existen. En una base creada antes de esta corrección, la columna del importe hay que ampliarla a mano una vez:

```sql
ALTER TABLE losses ALTER COLUMN total_amount TYPE numeric(19,2);
```

En una instalación nueva no hace falta: la tabla ya se crea con el tamaño correcto.

## Validación del despliegue en Azure

Realizada el 9 de octubre de 2026 sobre la versión `036be66` del backend, desplegada por el pipeline.

| Qué se ejecutó | Resultado |
|---|---|
| Recorrido de la API con Postman (Newman) contra la URL pública del backend | 30 peticiones, 49 de 49 comprobaciones |
| Pipeline del backend en `master` (tests, imagen, publicación y despliegue) | Correcto |
| Pipeline del frontend en `main` (tests, compilación y despliegue) | Correcto |
| Sitio web publicado, abierto en un navegador | Carga y se comunica con el backend |

### Qué se verificó

- **Salud y versión.** `/actuator/health` y `/actuator/health/readiness` responden `UP`, con la base conectada. `/actuator/info` informa el mismo commit que desplegó el pipeline.
- **Registro, login y JWT.** Alta de un usuario, rechazo del email repetido (`409`), de datos inválidos (`400`) y de la contraseña incorrecta (`401`). Sin token o con un token inválido, los endpoints responden `401`.
- **Reglas de negocio contra la base real.** Productos, vencimientos, notificaciones y pérdidas dan los mismos resultados que en los tests: un producto vencido de 2 unidades a 100 genera una pérdida de 200 ARS, que queda en el historial al eliminar el producto.
- **Migraciones.** La base de Azure se creó vacía y Flyway aplicó el esquema al arrancar.
- **HTTPS.** La URL responde por HTTPS y redirige las peticiones HTTP.
- **CORS.** El backend acepta peticiones desde el sitio publicado y rechaza con `403` las de otro origen.
- **Logs.** Cada petición queda registrada con método, ruta, código y duración. En los logs revisados no aparecen tokens, contraseñas ni emails.
- **Despliegue continuo.** Tres despliegues consecutivos del backend quedaron activos con la etiqueta de su commit (`sha-178bdfa` a mano, `sha-b6f1b14` y `sha-036be66` por el pipeline).

### Incidentes durante el despliegue

| Qué pasó | Cómo se resolvió |
|---|---|
| El pipeline falló al descargar imágenes públicas por el límite de descargas anónimas (`429`) | Las imágenes de Maven y PostgreSQL se piden a tres orígenes, con reintentos. El `Dockerfile` no cambió |
| El entorno de Container Apps se creó en un modo que no admite identidades administradas | Se recreó en modo de perfiles de carga, plan de consumo |

### Qué no se verificó

- Carga con muchos usuarios a la vez. La aplicación corre en una sola réplica.
- Qué pasa si la base de Azure se detiene con la aplicación en marcha. No se probó en el entorno desplegado.
- La app en iOS contra el backend de Azure: Expo Go para iOS no admite el SDK 53 del proyecto. Android se verificó después (ver la sección siguiente).
- El usuario de prueba creado durante el recorrido con Postman queda en la base: la API no tiene un endpoint para borrar usuarios.

## Validación de las correcciones (octubre de 2026)

Después de la evaluación de Ingeniería de Software Aplicada se corrigieron los puntos observados y se sumaron mejoras pedidas por el programa de Programación II. Esta sección registra solo lo que se comprobó de verdad, y cómo.

| Qué se cambió | Cómo se verificó | Resultado |
|---|---|---|
| CI y CD en workflows separados (`ci.yml` y `cd.yml`) | Cambio solo en el README en un Pull Request (PR #42): el CI probó y construyó la imagen, y el CD no se ejecutó. Al mergearlo no corrió ni el CI ni el CD | Correcto |
| Registro de cada petición con identificador (`X-Request-Id`) y usuario | Peticiones con identificadores propios y registro repetido (`201` y `409`), buscados después en los logs de Azure. Recorrido en la web con las líneas `usuario=<id>` | Correcto |
| Coordinación de pérdidas en la base (`SELECT … FOR UPDATE`) en lugar de `UserLocks` | Pruebas de concurrencia que fallaban sin el bloqueo y pasan con él, en H2 y en PostgreSQL 16 real (job *Concurrencia en PostgreSQL* del CI) | Correcto |
| Cierre controlado (`server.shutdown: graceful`) | Test con un pedido en curso que termina con `200` antes del apagado; `docker stop` en el CI; aplicación detenida en Azure y mensajes de cierre ordenado en Log Analytics | Correcto |
| Application Insights (agente en la imagen) | Peticiones (`POST /register` 201 y 409, `POST /login` 200) y consultas a PostgreSQL recibidas bajo el nombre `expirycontrol-backend` | Correcto |
| Métricas de Actuator cerradas | `/actuator/metrics` y `/actuator/prometheus` responden `401` sin token y `404` con token (`HealthIntegrationTest`) | Correcto en los tests |
| Frontend con Axios | Navegador automático contra el backend de Azure: registro (`201`), registro repetido (`409`), login fallido (`401`) y correcto, crear y eliminar un producto | Correcto |
| Postman y la web usan la misma API y la misma base | Usuario y producto creados directamente por la API y vistos después en la web pública | Correcto |
| Botón para mostrar u ocultar la contraseña | Tests del componente y navegador real en tamaño computadora y celular, en el login y en el registro | Correcto |
| App en Android | Emulador de Android Studio (Pixel 6, Android 14) contra el backend de Azure: inicio de sesión y creación de un producto | Correcto, probado por el alumno |
| Cambio de contraseña (`PUT /account/password`, PR #60 en el backend y PR #28 en el frontend) | Tests nuevos: 9 en el backend y 8 en el frontend. Backend local con `curl`: sin token `401`, contraseña actual incorrecta `400`, nueva corta `400`, cambio correcto `204`; después, login con la anterior `401` y con la nueva `200`. Los logs muestran `usuario=<id>` y ninguna contraseña. Navegador contra el backend local: validaciones del formulario, error de contraseña actual sin cerrar la sesión, aviso de éxito y login con la nueva, en tamaño computadora y celular. En Azure: el endpoint desplegado responde `401` sin token y la web publicada incluye la pantalla nueva | Correcto en local y en los tests. En Azure no se cambió la contraseña de ningún usuario real |

### Tests automáticos al cierre

| Proyecto | Tests |
|---|---|
| Backend | 144 de 144 |
| Frontend | 79 de 79 |

### Qué sigue sin verificarse

- La app en iOS (por la versión de Expo Go).
- Carga con muchos usuarios a la vez.
- El funcionamiento con más de una réplica en Azure: la coordinación entre réplicas se probó con hilos simultáneos y en PostgreSQL real, pero en Azure corre una sola réplica, por costo.
