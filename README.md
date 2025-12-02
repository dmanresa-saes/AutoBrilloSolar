# AutoBrillo Solar

Aplicación Android que ajusta automáticamente el brillo del sistema combinando la luz medida por la cámara y la posición solar (amanecer/ocaso). El objetivo es ofrecer un inicio seguro al encender la pantalla, reducir el consumo energético y respetar ajustes manuales del usuario.

## Características principales

- **Servicio en primer plano** que se activa con `ACTION_SCREEN_ON` y se detiene con `ACTION_SCREEN_OFF`.
- **Ajuste inicial inmediato**: al encender la pantalla, aplica 30 % si es de noche o 80 % si es de día y luego recalcula con la cámara.
- **Medición puntual con cámara trasera** usando CameraX (un único frame por ciclo para minimizar el tiempo de uso del sensor).
- **Lógica solar configurable**: botón "Actualizar ubicación solar" para recalcular amanecer/ocaso cuando el usuario lo desee. Si no hay permisos o señal GPS, usa un horario por defecto (07:00 / 20:00).
- **WorkManager** programa una medición cada 5 minutos mientras la pantalla esté encendida, con transición suave de brillo (≈350 ms).
- **Offset manual** de ±20 % accesible desde la notificación y la actividad principal.
- **Detección de ajustes manuales** mediante `ContentObserver`; pausa las mediciones durante 10 min si el usuario modifica el brillo global.
- **Botón “Modo noche (0 %)”**: toggle de alto contraste que fija el brillo al 0 % hasta que el usuario lo desactive (la lógica automática se reanuda al volver al modo normal).

## Estructura del proyecto

```
app/
 ├─ src/main/java/com/autobrillo/solar/
 │   ├─ data/                # DataStore de preferencias (offset, pausas, horarios solares)
 │   ├─ domain/              # BrightnessManager, SunTimesCalculator, CameraLightMeter, etc.
 │   ├─ service/             # AutoBrightnessService + notificación foreground
 │   ├─ work/                # BrightnessWorker y WorkScheduler (WorkManager)
 │   ├─ ui/                  # MainActivity y OffsetActivity
 │   └─ receiver/            # ScreenStateReceiver para SCREEN_ON/OFF
 └─ src/main/res/            # Layouts, drawables, strings, temas
```

## Flujo de brillo

1. `ScreenStateReceiver` recibe `ACTION_SCREEN_ON` y arranca `AutoBrightnessService`.
2. El servicio aplica un brillo inicial fijo (30 % noche / 80 % día) usando los horarios almacenados.
3. Programa inmediatamente un trabajo de `BrightnessWorker` que:
   - Verifica si existe una anulación manual o pausa del usuario.
   - Lee los horarios guardados (o el fallback 07:00/20:00) para decidir el límite nocturno (30 %) vs. diurno (100 %).
   - Mide la luz con la cámara trasera, calcula el objetivo aplicando offset y suaviza el cambio.
4. Cada 5 min el `WorkManager` repite el proceso mientras la pantalla siga encendida. Si detecta una interacción manual con el brillo, pausa los trabajos durante 10 min.

## Requisitos de permisos

- `WRITE_SETTINGS`: se solicita mediante Intent al panel del sistema (botón "Permitir control de brillo").
- `CAMERA`: para la medición puntual de luz (la app no deja la cámara abierta entre mediciones).
- `ACCESS_COARSE_LOCATION`: únicamente cuando el usuario pulsa "Actualizar ubicación solar".
- `POST_NOTIFICATIONS` (Android 13+): para mostrar la notificación persistente con acción Pausar/Reanudar.

## Uso

1. Instala y abre la aplicación.
2. Pulsa **Solicitar cámara y ubicación** y concede los permisos (en Android 13+ también notificaciones).
3. Pulsa **Permitir control de brillo** para abrir la pantalla de `WRITE_SETTINGS` y autorizar el cambio global.
4. Opcionalmente pulsa **Actualizar ubicación solar** cuando quieras recalcular amanecer/ocaso (por ejemplo si viajaste a otra ciudad). Mientras no lo hagas se mantendrá el último cálculo o, en su defecto, el horario fijo 07:00/20:00.
5. Activa **Modo noche (0 %)** para un brillo fijo mínimo (ideal para usar el móvil en la cama). Pulsa de nuevo para volver al modo automático.
6. Ajusta un offset en **Ajustar Offset** (+/- 20 %) si deseas un margen personal permanente.
6. Activa el servicio con **Iniciar / reactivar servicio** (o simplemente bloquea/desbloquea la pantalla; el receptor se encarga del ciclo).

## Desarrollo

- **Compilar**: `./gradlew :app:assembleDebug`
- **Lint**: `./gradlew :app:lintDebug`
- **Instalar en un dispositivo**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

### Pruebas útiles

- Forzar una medición inmediata del WorkManager (sin esperar 5 min):
  ```bash
  adb shell cmd jobscheduler run -f com.autobrillo.solar.debug <jobId>
  ```
  El `jobId` activo se puede obtener con `adb shell dumpsys jobscheduler | grep -n autobrillo`.
- Simular encendido/apagado de pantalla para observar el brillo inicial:
  ```bash
  adb shell input keyevent 223   # SCREEN_OFF
  adb shell input keyevent 224   # SCREEN_ON
  ```
- Revocar permisos para repetir el flujo de diálogos:
  ```bash
  adb shell pm revoke com.autobrillo.solar.debug android.permission.CAMERA
  adb shell pm revoke com.autobrillo.solar.debug android.permission.ACCESS_COARSE_LOCATION
  ```

## Roadmap

- Confirmar precisión de la medición con cámara trasera en distintos dispositivos y ajustar la conversión lux/percent según sea necesario.
- Añadir métricas internas (por ejemplo `Logcat` categorizado) para analizar tiempos entre encendido y primera medición.
- Exportar configuración (offset, horarios solares) a un pequeño panel de depuración para facilitar QA/manual testing.
