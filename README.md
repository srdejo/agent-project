import os
import weasyprint

# 1. Define README.md content
readme_markdown = """# 🤖 Agent Project

> **Un estándar común para que los agentes de IA puedan entender, actualizar y reportar el estado de un proyecto de software.**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Status](https://img.shields.io/badge/Status-En%20Definici%C3%B3n-orange)](#-estado-actual)
[![Author](https://img.shields.io/badge/Author-Daniel%20Jimenez-blue)](https://srdejo.github.io)

---

## 📋 Tabla de Contenidos
- [🎯 El Problema](#-el-problema)
- [💡 La Idea](#-la-idea)
- [📁 Estructura Propuesta](#-estructura-propuesta)
  - [ROADMAP.md](#roadmapmd)
  - [TASKS.md](#tasksmd)
  - [PROGRESS.md](#progressmd)
  - [AGENTS.md](#agentsmd)
- [🔄 Flujo de Trabajo](#-flujo-de-trabajo)
- [📊 Estado Estandarizado](#-estado-estandarizado)
- [📐 Principios del Estándar](#-principios-del-estándar)
- [🧩 Modelo Conceptual](#-modelo-conceptual)
- [📊 Visualización](#-visualización)
- [🤖 Integración con Agentes](#-integración-con-agentes)
- [🧠 ¿Por qué no usar solamente Git?](#-por-qué-no-usar-solamente-git)
- [🗄️ ¿Se necesita una base de datos?](#️-se-necesita-una-base-de-datos)
- [🌐 Monitoreo Multi-proyecto](#-monitoreo-multi-proyecto)
- [🔮 Visión](#-visión)
- [🗺️ Roadmap del Estándar](#️-roadmap-del-estándar)
- [🚧 Estado Actual](#-estado-actual)
- [🤝 Contribuciones](#-contribuciones)
- [📄 Licencia](#-licencia)
- [👨‍💻 Autor](#-autor)

---

## 🎯 El Problema

El desarrollo de software asistido por IA puede generar una gran cantidad de información distribuida e inconexa:

* 💬 Conversaciones y chats con múltiples agentes
* 🗺️ Roadmaps y planes de trabajo
* 📝 Listas de tareas informales
* 📈 Archivos temporales de progreso
* 🏗️ Decisiones técnicas y arquitectura
* 🔀 Commits, PRs e Issues en Git
* 📚 Documentación fragmentada
* 🧠 Notas e instrucciones específicas para el agente

Con el crecimiento del proyecto, responder preguntas fundamentales sobre el avance se vuelve complejo:

> **¿Qué se ha completado?**  
> **¿En qué está trabajando actualmente el agente?**  
> **¿Cuál es la siguiente tarea prioritaria?**  
> **¿Qué porcentaje del proyecto está realmente terminado?**  
> **¿Existen bloqueadores que detengan el desarrollo?**  
> **¿Cuál fue el último avance registrado?**

Dado que cada agente de IA (Cursor, Claude Code, OpenClaw, Codex, Copilot, etc.) utiliza su propio criterio o formato para gestionar esta información, surge la necesidad de una especificación unificada.

**Agent Project busca establecer un lenguaje y estructura común para resolver esta fragmentación.**

---

## 💡 La Idea

Agent Project propone que todo proyecto asistido por IA mantenga una **estructura estandarizada de archivos Markdown** en la raíz del repositorio:

```text
                     Proyecto
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
   ROADMAP.md        TASKS.md      PROGRESS.md
        │               │               │
        └───────────────┼───────────────┘
                        │
                        ▼
                  Agent Project
                        │
                        ▼
                 Estado Estándar
                        │
             ┌──────────┴──────────┐
             ▼                     ▼
          Agentes             Herramientas
             │                     │
             ▼                     ▼
       Actualización         Visualización
