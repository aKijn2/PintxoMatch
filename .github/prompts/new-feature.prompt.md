---
description: Plantilla reutilizable para crear una nueva feature de Food View X respetando Compose, MVVM, StateFlow, Firebase y Clean Architecture.
---

# New Feature Template - Food View X

Usa esta plantilla antes de implementar una nueva pantalla o feature.

## 1. Contexto de la Feature

- Nombre de la feature:
- Problema de usuario que resuelve:
- Flujo principal:
- Pantallas afectadas:
- Dependencias Firebase:
- Riesgos o edge cases:

## 2. Reglas Obligatorias

- UI solo con Jetpack Compose.
- Modo oscuro por defecto.
- Acentos en rojo corporativo.
- MVVM con `ViewModel`.
- Estado expuesto con `StateFlow`.
- Separación por capas `ui`, `data`, `domain`, `di`, `utils`.
- Sin lógica de negocio compleja dentro de composables.

## 3. Diseño por Capas

### UI

- Pantalla contenedora:
- Componentes stateless reutilizables:
- Estados visuales: loading / success / error / empty:
- Navegación requerida:

### Domain

- Reglas de negocio:
- Modelos de dominio:
- Validaciones:

### Data

- Repositorios afectados:
- Lecturas/escrituras Firebase:
- Necesidad de transacciones:
- Estrategia de paginación o acotación:

## 4. ViewModel

- Eventos de entrada:
- `UiState`:
- Side effects:
- Manejo de errores:

## 5. Checklist de Implementación

1. Definir o ajustar modelos.
2. Implementar repositorio y reglas de negocio.
3. Exponer estado desde ViewModel con `StateFlow`.
4. Construir UI Compose manteniendo componentes stateless.
5. Añadir animaciones solo donde aporten valor.
6. Actualizar imports, navegación y dependencias.
7. Verificar compilación.

## 6. Checklist de Calidad

- ¿La lógica de negocio quedó fuera de la UI?
- ¿La feature respeta modo oscuro y acentos rojos?
- ¿Se evitaron lecturas remotas innecesarias?
- ¿Se usan transacciones si hay XP/rachas/badges?
- ¿La pantalla tiene estados vacíos y de error claros?
- ¿Compila el proyecto al finalizar?
