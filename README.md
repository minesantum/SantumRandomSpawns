# SantumRandomSpawns

**SantumRandomSpawns** es un plugin para servidores de Minecraft diseñado para gestionar la aparición personalizada y aleatoria de NPCs o entidades por grupos (*clusters*), aplicando reglas específicas por mundo, evaluadores de condiciones dinámicas y limpieza automática de criaturas vanilla.

---

## Características Principales

* **Spawning por Clústeres (`ClusterRandomSpawner`):** Genera grupos de entidades en ubicaciones aleatorias de forma natural y controlada.
* **Reglas por Mundo (`WorldSpawnRule`):** Configura qué apariciones están permitidas en cada mundo de tu servidor.
* **Evaluador de Condiciones (`ConditionEvaluator`):** Sistema flexible para validar requisitos (biomas, altura, rangos numéricos, tiempo, etc.) antes de permitir un spawn.
* **Gestión y Seguimiento de NPCs (`NpcSpawnerService` & `TrackedNpc`):** Controla el ciclo de vida de los NPCs generados, evitando sobrecargas y manteniendo un registro activo.
* **Limpiador de Mobs Vanilla (`VanillaMobCleaner`):** Permite filtrar o eliminar mobs predeterminados de Minecraft para dar protagonismo a los spawns personalizados.
* **Configuración Modular:** Archivos YAML individuales dentro de la carpeta `randomspawns/` para organizar fácilmente cada entidad o evento de aparición.

---

## Requisitos

* **Servidor:** Spigot / Paper (o derivados compatibles).
* **Java:** Java 17 o superior (recomendado según el entorno de compilación Gradle).

---

## Instalación

1. Descarga o compila el archivo `SantumRandomSpawns-1.0.0.jar`.
2. Coloca el archivo `.jar` dentro de la carpeta `plugins/` de tu servidor.
3. Inicia o reinicia el servidor para que se generen los archivos de configuración por defecto (`config.yml` y la carpeta `randomspawns/`).
4. Edita las configuraciones según tus necesidades y recarga el plugin.

---

## Estructura de Archivos

Una vez iniciado el servidor, el plugin generará la siguiente estructura en `plugins/SantumRandomSpawns/`:

```text
plugins/SantumRandomSpawns/
├── config.yml                 # Configuración general del plugin y opciones del VanillaMobCleaner
└── randomspawns/
    └── ejemplo.yml            # Archivo de plantilla con ejemplos de reglas y condiciones
```

### Ejemplo de Configuración de Spawn (`randomspawns/ejemplo.yml`)

Puedes crear tantos archivos `.yml` como desees dentro de `randomspawns/`. Cada archivo define las reglas de aparición:

```yaml
# Ejemplo ilustrativo de estructura basada en los modelos del plugin
ejemplo_npc:
  worlds:
    - world
  cluster_size: 1-3
  conditions:
    - "y >= 60"
    - "y <= 120"
  chance: 0.5
```
*(Nota: Ajusta las claves exactas según la plantilla generada en `src/main/resources/randomspawns/ejemplo.yml`).*

---

## Comandos y Permisos

El plugin cuenta con un comando principal gestionado por `SantumCommand`:

| Comando | Descripción | Permiso sugerido |
| :--- | :--- | :--- |
| `/santumrandomspawns` (o alias configurado en `plugin.yml`) | Comando principal de administración (recarga de configuración, información o spawn manual). | `santumrandomspawns.admin` |

*(Revisa `plugin.yml` para verificar los alias exactos y nodos de permisos registrados).*

---

## Compilación desde el Código Fuente

Este proyecto utiliza **Gradle (Kotlin DSL)**. Si deseas compilar el plugin por tu cuenta:

1. Clona este repositorio o extrae el código fuente.
2. Abre una terminal en la raíz del proyecto (`SantumRandomSpawns/`).
3. Ejecuta el siguiente comando:

```bash
./gradlew build
```

4. El archivo compilado se generará en `build/libs/SantumRandomSpawns-1.0.0.jar`.

---

## Arquitectura del Proyecto

El código está organizado bajo el paquete `com.donkolia.santumrandomspawns`:

* `commands/` — Manejo de comandos del plugin (`SantumCommand`).
* `conditions/` — Lógica y operadores de evaluación de requisitos (`ConditionEvaluator`).
* `config/` — Lectura y carga de archivos YAML (`RandomSpawnLoader`).
* `listeners/` — Eventos de aparición y control del mundo (`SpawningListener`).
* `model/` — Modelos de datos (`IntRange`, `RandomSpawnConfig`, `WorldSpawnRule`).
* `spawning/` — Motores de generación, rastreo de NPCs y limpieza (`ClusterRandomSpawner`, `NpcSpawnerService`, `TrackedNpc`, `VanillaMobCleaner`).

---

## Autor

* **DonKolia** — *Desarrollo inicial*