# 🚀 Roadmap de Mejoras: De Beta a Lanzamiento

## 1. Experiencia de Usuario (UX) y Onboarding
El primer contacto de los usuarios con la app debe ser impecable y guiado.

### Tutorial de Inicio (First Launch)
* **Formato ideal:** Pantalla completa al abrir la app por primera vez, seguido de pequeñas ayudas visuales (*coach marks*) al entrar a las pantallas principales.
* **Flujo de 3 a 5 pasos:** Explica qué hace la app, cómo subir un pintxo, cómo valorar/comentar y dónde encontrar soporte.
* **Regla de oro:** Nunca bloquees al usuario. Incluye siempre un botón de **Saltar (Skip)** y permite volver a ver el tutorial desde los Ajustes.
* **Permisos contextuales:** Pide acceso a la cámara o ubicación solo cuando el usuario vaya a usarlos, no de golpe al abrir la app.

### Estados Vacíos (Empty States) Accionables
Si una pantalla no tiene datos, no la dejes en blanco. Muestra una llamada a la acción clara:
* **Sin reseñas:** "Sé el primero en dejar una reseña."
* **Sin subidas:** "Sube tu primer pintxo."
* **Sin amigos:** "Explora otros usuarios de tu zona."

### Pulido Final (Polish)
* **Accesibilidad:** Mejora las descripciones de contenido, permite escalar el tamaño de la fuente y revisa el contraste de colores.
* **Interfaz:** Añade *skeletons* (pantallas de carga grises con la forma del contenido) y transiciones suaves en los flujos principales.

---

## 2. Retención y Engagement
Estrategias para que los usuarios vuelvan a la app semanalmente.

### Gamificación básica
* [x] Añade rachas (*streaks*) o retos semanales (ej. "Valora 3 pintxos esta semana" o "Sube 1 pintxo nuevo").
* [x] Recompensa a los usuarios con pequeñas insignias (*badges*) en su perfil o un sistema de puntos/experiencia (XP).

Estado actual (2026-04-01):
* [x] Modelo de usuario ampliado con `xp`, `currentStreak`, `lastActionTimestamp` y `badges`.
* [x] Lógica transaccional en Firestore para otorgar XP (+10 valorar, +50 subir).
* [x] Comprobación de reto semanal + desbloqueo de badge.
* [x] UI de perfil gamificada con nivel, racha y badges.
* [x] Tarjetas de retos semanales con barra de progreso animada.

Próximo incremento:
* [x] Notificación premium al desbloquear badge (popup visual con animación y branding de Food View X).

---

## 3. Confianza, Calidad y Rendimiento (Ingeniería)
Para escalar la app, la base técnica debe ser sólida y el contenido debe estar moderado.

### Capa de Confianza y Moderación
* Valida el tamaño y las dimensiones de las imágenes antes de subirlas para ahorrar costes de servidor.
* Añade botones de "Reportar reseña" o "Reportar imagen".
* Implementa un filtro básico de groserías para los textos.

### Estabilidad y Rendimiento
* Revisa la estrategia de caché para la carga de imágenes (fundamental para un feed vertical estilo TikTok).
* Configura reintentos automáticos (*exponential backoff*) para cuando falle la red.
* Mejora el manejo del modo sin conexión (avisa al usuario de forma elegante si no hay internet).
* Añade eventos de analíticas y reporte de *crashes* en los flujos críticos (subida de fotos y reseñas).