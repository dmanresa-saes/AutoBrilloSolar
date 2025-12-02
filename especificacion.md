# 📝 Especificación de Requisitos de Software (SRS) - AutoBrillo Solar v3.0

## 1. Introducción

El objetivo de la aplicación **AutoBrillo Solar** es ajustar dinámicamente el brillo de la pantalla del dispositivo Android basándose en la luminosidad ambiental capturada por la cámara frontal y la hora del día (amanecer/ocaso), priorizando la **máxima eficiencia de batería** y la comodidad visual.

---

## 2. Requisitos Funcionales (RF)

| ID | Requisito Funcional | Descripción |
|---|---|---|
| **RF001** | **Control de Brillo Global** | La aplicación debe ser capaz de establecer el brillo de la pantalla del sistema operativo (que afecta a todas las aplicaciones), utilizando el permiso `WRITE_SETTINGS`. |
| **RF002** | **Medición Única de Cámara** | El proceso de medición de luz debe abrir la **cámara frontal**, capturar **un único *frame*** para calcular la luminosidad promedio, y **cerrar inmediatamente la cámara**. |
| **RF003** | **Activación del Servicio** | El servicio principal debe ejecutarse como un **Servicio en Primer Plano** (`Foreground Service`) que se inicia o reactiva únicamente al recibir `ACTION_SCREEN_ON`. |
| **RF004** | **Requisito de la Cámara** | El uso de la cámara (RF002) es obligatorio debido a la indisponibilidad o falla del sensor de iluminación de bajo consumo en el dispositivo objetivo. |
| **RF005** | **Desactivación Completa** | El **Servicio en Primer Plano** debe detenerse y el programador de tareas (`WorkManager`) debe cancelarse cuando la pantalla se apague (`ACTION_SCREEN_OFF`). |
| **RF006** | **Ajuste de Inicio Seguro** | Al recibir `ACTION_SCREEN_OFF`, la aplicación debe **forzar instantáneamente** el brillo del sistema al **mínimo absoluto** (Ej: 5%) para garantizar un inicio seguro. |
| **RF007** | **Cálculo Astronómico** | La aplicación debe calcular las horas exactas de **Amanecer** y **Ocaso** (`sunrise`/`sunset`) utilizando la ubicación geográfica actual. |
| **RF008** | **Lógica de Atenuación Nocturna** | Al calcular el Brillo Final, se debe aplicar un **Límite Máximo de Brillo** basado en el tiempo solar (RF007): **Noche** (Ocaso - Amanecer) el límite es del **30%**. **Día** (Amanecer - Ocaso) el límite es del **100%**. |
| **RF009** | **Fallback de Atenuación Nocturna** | Si el permiso de ubicación es denegado o no está disponible, la aplicación debe revertir el cálculo de Amanecer/Ocaso a un **horario fijo por defecto** (Ej: Ocaso a 20:00h y Amanecer a 07:00h). |
| **RF010** | **Transición Suave** | Todos los cambios de brillo deben aplicarse con una **transición suave** (interpolación) de **300 a 500 milisegundos**. |
| **RF011** | **Interfaz de Estado Inicial** | La aplicación debe tener una **Actividad principal** que: 1) Guíe al usuario para otorgar permisos. 2) Muestre el estado (Activa / Pausada). 3) Muestre los valores de Amanecer y Ocaso. |
| **RF012** | **Acción de Pausa/Reanudar** | La **Notificación Persistente** debe incluir un botón de acción para que el usuario pueda **Pausar** y **Reanudar** el servicio de ajuste automático. |
| **RF013** | **Modo de Anulación Temporal (Monitoreo)** | La aplicación debe monitorear los cambios en la configuración global de brillo del sistema (usando un **`ContentObserver`**) para detectar ajustes manuales del usuario y **pausar** el `WorkManager` (RF015) temporalmente. |
| **RF014** | **Acceso Rápido a Ajuste Fino** | El toque en la **Notificación Persistente** debe abrir una interfaz para establecer un **desplazamiento de brillo** (*offset*) (+/- 20% del valor calculado automáticamente). |
| **RF015** | **Re-Medición Periódica (Adaptación Lenta)** | Al encenderse la pantalla, un **`WorkManager`** debe programar una tarea repetitiva cada **5 minutos** (o un intervalo configurable), llamando a RF002 y aplicando RF010 si el brillo ha cambiado. |
| **RF016** | **Manejo de Revocación de Permiso** | Si la aplicación falla al intentar abrir la cámara (debido a la revocación del permiso `CAMERA`), debe detener inmediatamente el servicio, notificar al usuario y guiarlo a la configuración para reactivarlo. |

---

## 3. Requisitos No Funcionales (RNF)

| ID | Requisito No Funcional | Descripción |
|---|---|---|
| **RNF001** | **Rendimiento** | El tiempo de latencia para el **ajuste inicial** (al encender) no debe exceder los 500 milisegundos. |
| **RNF002** | **Batería** | La cámara debe permanecer **apagada** y el consumo de batería debe ser mínimo, salvo por las ejecuciones periódicas del `WorkManager` (RF015). |
| **RNF003** | **Compatibilidad (Target Específico)** | La solución debe ser totalmente compatible y probada para un **Samsung A40** que ejecuta una versión moderna de Android. |
| **RNF004** | **Tasa de Refresco** | La aplicación solo medirá la luz al encender y cada **5 minutos** mientras la pantalla esté activa. |

---

## 4. Requisitos de Interfaz y Permisos (RI)

| ID | Tipo | Permiso/Configuración | Notas |
|---|---|---|---|
| **RI001** | **Permiso de Sistema** | `WRITE_SETTINGS` | Se debe guiar al usuario a la pantalla de configuración para la concesión manual. |
| **RI002** | **Permiso de Hardware** | `CAMERA` | Se debe solicitar en tiempo de ejecución. |
| **RI003** | **Permiso de Ubicación** | `ACCESS_COARSE_LOCATION` | Necesario para calcular Amanecer/Ocaso (RF007). |
| **RI004** | **Notificación Persistente** | Interfaz de Notificación | Debe mostrar una notificación no cancelable mientras el Servicio en Primer Plano esté activo, incluyendo el botón de acción **Pausar/Reanudar** (RF012). |

---

## 5. Requisitos del Entorno de Desarrollo (RED)

| ID | Requisito del Entorno | Descripción |
|---|---|---|
| **RED001** | **Sistema Operativo** | **Debian Linux 13 ("Trixie")** es el sistema operativo de desarrollo. |
| **RED002** | **Hardware** | Estación de trabajo **Dell Latitude E5520** con procesador **Intel Core i3** y **12 GB de RAM**. |
| **RED003** | **IDE** | El entorno de desarrollo integrado principal debe ser **Android Studio** (última versión estable). |
| **RED004** | **Dispositivo de Prueba** | El dispositivo de prueba primario será el **Samsung A40**, conectado vía USB con la **Depuración USB** habilitada. |
| **RED005** | **Dependencias** | Se deben instalar las librerías de compatibilidad de 32 bits necesarias para el correcto funcionamiento del SDK de Android en el entorno Debian de 64 bits. |
