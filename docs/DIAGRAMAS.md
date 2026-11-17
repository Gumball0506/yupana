# Diagramas de Yupana (se ven en GitHub; el código es Mermaid)

## 1. Flujo del usuario
```mermaid
flowchart LR
    A[Ingresar] -->|5 fallos seguidos: bloqueo 15 min| A
    A --> B{Rol}
    B -->|Estudio| C[Panel de empresas<br/>crear, editar, desactivar]
    B -->|MYPE| E
    C --> D[Abrir una empresa]
    D --> E[Cargar compras + propuesta RCE<br/>o pedir propuesta al simulador SIRE]
    E -->|archivo inválido| E1[Error con fila y columna<br/>no se guarda nada]
    E --> F[Conciliar]
    F --> G[Panel: 3 de 10 no cuadran<br/>IGV bajo revisión]
    G --> H[Confirmar / Observar cada caso]
    H --> I[Aviso del día y chat simulado]
    H -->|volver a conciliar| F
```

## 2. Arquitectura por capas (las flechas son dependencias)
```mermaid
flowchart TB
    web[web<br/>controladores, errores, cabeceras, límites]
    app[carga · conciliacion · casos · empresas · acceso · sire · mensajes · uso<br/>servicios y puertos]
    panel[panel<br/>consulta de solo lectura]
    dom[dominio<br/>Conciliador, LlaveComprobante, Ruc, reglas puras]
    seg[seguridad<br/>Claves, Sesion, auditoría, límites]
    per[persistencia<br/>adaptadores JDBC: único lugar con SQL]
    cfg[config<br/>propiedades, siembra]
    web --> app
    web --> panel
    panel --> app
    app --> dom
    per -->|implementa los puertos| app
    app --> seg
    cfg --> app
    classDef puro fill:#e6f2ea,stroke:#2e7d52
    class dom puro
```
`ArquitecturaTest` verifica: el dominio no importa Spring ni JDBC; solo `persistencia` usa SQL; la web no toca la persistencia; nadie depende de `panel`.

## 3. Despliegue
```mermaid
flowchart LR
    U[Navegador] -->|HTTPS 443| C[Caddy<br/>certificado automático]
    C -->|http 8770| A[app<br/>Spring Boot, perfil produccion]
    A -->|JDBC| D[(PostgreSQL 16<br/>volumen pgdata)]
    R[respaldo<br/>pg_dump cada 24 h] -->|lee| D
    R -->|7 diarios + 4 semanales| V[(./respaldos)]
    A -.->|/actuator/health| H[healthcheck de Docker]
```

## 4. Secuencia: ingreso con bloqueo
```mermaid
sequenceDiagram
    actor U as Usuario
    participant W as AccesoController
    participant S as ServicioAcceso
    participant L as LimitadorDeIntentos
    participant B as Base de datos
    U->>W: POST /api/login
    W->>W: límite de peticiones por IP (20/min)
    W->>S: ingresar(usuario, clave, ip)
    S->>L: ¿bloqueado el usuario o la IP?
    alt bloqueado
        S-->>U: 429 + Retry-After (auditoría INGRESO_BLOQUEADO)
    else libre
        S->>B: buscar usuario
        S->>S: PBKDF2 (mismo tiempo exista o no el usuario)
        alt clave incorrecta
            S->>L: registrar fallo (usuario e IP)
            S-->>U: 401 «Usuario o clave incorrectos.»
        else correcta
            S->>L: reiniciar contador del usuario
            S-->>U: 200 + cookie de sesión nueva (HttpOnly, SameSite=Strict, Secure)
        end
    end
```

## 5. Secuencia: conciliar y revisar un caso
```mermaid
sequenceDiagram
    actor E as Estudio o MYPE
    participant C as ConciliacionController
    participant S as ServicioConciliacion
    participant D as Conciliador (dominio)
    participant P as Repositorios JDBC
    participant K as ServicioCasos
    E->>C: POST /api/conciliar
    C->>S: conciliar(empresa de la sesión)
    S->>P: últimas compras y propuesta RCE
    S->>D: comparar(compras, rce)
    D-->>S: resultados (CONCILIADO, DIFERENCIA, SOLO_COMPRAS, SOLO_RCE)
    S->>P: guardar conciliación, resultados y diferencias
    S-->>K: evento ConciliacionEjecutada (hereda revisiones)
    S-->>E: panel con IGV bajo revisión
    E->>C: POST /api/casos/{id}/estado (observar o confirmar)
    C->>K: revisar(empresa, caso, acción, nota)
    K->>P: guardar acción con fecha y usuario
```

## 6. Modelo de datos (ER)
```mermaid
erDiagram
    empresas ||--o{ usuarios : "tiene accesos MYPE"
    empresas ||--o{ importaciones : "carga"
    empresas ||--o{ conciliaciones : "concilia"
    empresas ||--o{ mensajes : "chat"
    empresas ||--o{ sire_tickets : "pide propuesta"
    importaciones ||--o{ comprobantes : "contiene"
    importaciones ||--o{ conciliaciones : "compras y RCE"
    conciliaciones ||--o{ resultados : "uno por comprobante"
    conciliaciones ||--o{ avisos : "aviso del día"
    resultados ||--o{ diferencias : "campo que difiere"
    resultados ||--o{ acciones_caso : "confirmar u observar"
    empresas {
        bigint id PK
        varchar ruc UK
        varchar razon_social
        varchar telefono
        boolean activa
    }
    usuarios {
        varchar usuario UK
        varchar clave_hash "PBKDF2"
        varchar rol "ESTUDIO o MYPE"
        bigint empresa_id FK
    }
    comprobantes {
        varchar ruc_proveedor
        varchar tipo_comprobante
        varchar serie
        varchar numero
        decimal base_imponible
        decimal igv
        decimal total
    }
    resultados {
        varchar resultado "CONCILIADO DIFERENCIA SOLO_COMPRAS SOLO_RCE"
        varchar causa
        decimal igv_compras
        decimal igv_rce
    }
    auditoria {
        varchar evento
        varchar detalle "sin datos de archivos"
    }
    eventos {
        varchar sesion "anónimo"
        varchar tipo
    }
    feedback {
        varchar respuesta
        varchar comentario
    }
```
