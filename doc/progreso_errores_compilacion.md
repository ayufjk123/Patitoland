# Progreso de Resolución de Errores

## Errores de Compilación (Classpath y Reconocimiento del JDK)
- **Problema:** "Implicit super constructor Object() is undefined." en `EmailService.java` y "The import java.util.Map cannot be resolved" en `ContactController.java`.
- **Causa:** El entorno de desarrollo (IDE) perdió la referencia a la librería base de Java (JRE System Library) o su caché está corrupta/desincronizada, a pesar de que el código compila correctamente desde la terminal (`mvn clean compile` exitoso).
- **Acción:** Se comprobó la integridad del código con Maven. Se actualizó el archivo `.classpath` de `JavaSE-19` a `JavaSE-21`.
- **Estado:** Pendiente de que el usuario limpie el espacio de trabajo de su IDE (Clean Workspace) o actualice el proyecto de Maven para forzar la reindexación de las librerías estándar de Java.
