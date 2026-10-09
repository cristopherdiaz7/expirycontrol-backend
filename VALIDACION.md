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
