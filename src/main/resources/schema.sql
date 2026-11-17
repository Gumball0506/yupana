-- ===== H5 · Multiempresa: cada dato pertenece a una empresa (RUC ficticio validado con dígito verificador) =====
CREATE TABLE IF NOT EXISTS empresas (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    ruc          VARCHAR(11)  NOT NULL UNIQUE,
    razon_social VARCHAR(100) NOT NULL,
    telefono     VARCHAR(15),             -- solo dígitos con código de país, para el enlace wa.me
    creada       VARCHAR(19)  NOT NULL
);

-- Usuarios: ESTUDIO ve todas las empresas; MYPE solo la suya. Clave guardada como PBKDF2 (nunca en texto).
CREATE TABLE IF NOT EXISTS usuarios (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario    VARCHAR(40)  NOT NULL UNIQUE,
    clave_hash VARCHAR(200) NOT NULL,
    rol        VARCHAR(10)  NOT NULL CHECK (rol IN ('ESTUDIO','MYPE')),
    empresa_id BIGINT REFERENCES empresas(id) ON DELETE CASCADE,
    creado     VARCHAR(19)  NOT NULL,
    CHECK ((rol = 'ESTUDIO' AND empresa_id IS NULL) OR (rol = 'MYPE' AND empresa_id IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS importaciones (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id     BIGINT       NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    origen         VARCHAR(10)  NOT NULL CHECK (origen IN ('compras', 'rce')),
    nombre_archivo VARCHAR(255) NOT NULL,
    fecha          VARCHAR(19)  NOT NULL,
    total_filas    INT          NOT NULL,
    advertencias   VARCHAR(4000)            -- avisos que no bloquean (columnas ignoradas, total ≠ base + IGV)
);

CREATE TABLE IF NOT EXISTS comprobantes (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_importacion   BIGINT        NOT NULL REFERENCES importaciones(id) ON DELETE CASCADE,
    ruc_proveedor    VARCHAR(11)   NOT NULL,
    tipo_comprobante VARCHAR(30)   NOT NULL,
    serie            VARCHAR(20)   NOT NULL,
    numero           VARCHAR(20)   NOT NULL,
    fecha_emision    VARCHAR(10)   NOT NULL,
    base_imponible   DECIMAL(14,2) NOT NULL,
    igv              DECIMAL(14,2) NOT NULL,
    total            DECIMAL(14,2) NOT NULL,
    UNIQUE (id_importacion, ruc_proveedor, tipo_comprobante, serie, numero)
);

-- Marketing 5.0 · datos de experiencia: eventos ANÓNIMOS (sesión aleatoria, nunca contenido de archivos)
CREATE TABLE IF NOT EXISTS eventos (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    sesion  VARCHAR(36) NOT NULL,
    tipo    VARCHAR(30) NOT NULL,
    detalle VARCHAR(40),
    fecha   VARCHAR(19) NOT NULL
);

-- ===== 2.1 Conciliación: se registra cada ejecución con origen y causa =====
CREATE TABLE IF NOT EXISTS conciliaciones (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id      BIGINT        NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    id_compras      BIGINT        NOT NULL REFERENCES importaciones(id) ON DELETE CASCADE,
    id_rce          BIGINT        NOT NULL REFERENCES importaciones(id) ON DELETE CASCADE,
    fecha           VARCHAR(19)   NOT NULL,
    total           INT           NOT NULL,
    conciliados     INT           NOT NULL,
    con_diferencia  INT           NOT NULL,
    igv_revision    DECIMAL(14,2) NOT NULL,  -- indicativo, NO es crédito fiscal perdido
    diferencia_igv  DECIMAL(14,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS resultados (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_conciliacion  BIGINT       NOT NULL REFERENCES conciliaciones(id) ON DELETE CASCADE,
    orden            INT          NOT NULL,
    ruc_proveedor    VARCHAR(11)  NOT NULL,
    tipo_comprobante VARCHAR(30)  NOT NULL,
    serie            VARCHAR(20)  NOT NULL,
    numero           VARCHAR(20)  NOT NULL,
    resultado        VARCHAR(15)  NOT NULL CHECK (resultado IN ('CONCILIADO','DIFERENCIA','SOLO_COMPRAS','SOLO_RCE')),
    causa            VARCHAR(400),
    igv_compras      DECIMAL(14,2),
    igv_rce          DECIMAL(14,2)
);

CREATE TABLE IF NOT EXISTS diferencias (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_resultado  BIGINT      NOT NULL REFERENCES resultados(id) ON DELETE CASCADE,
    campo         VARCHAR(20) NOT NULL,
    valor_compras VARCHAR(30) NOT NULL,
    valor_rce     VARCHAR(30) NOT NULL
);

-- ===== 3.1-B Confirmar / Observar: cada acción queda registrada con fecha y quién la hizo =====
CREATE TABLE IF NOT EXISTS acciones_caso (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_resultado BIGINT       NOT NULL REFERENCES resultados(id) ON DELETE CASCADE,
    accion       VARCHAR(10)  NOT NULL CHECK (accion IN ('CONFIRMADO','OBSERVADO')),
    nota         VARCHAR(300),
    fecha        VARCHAR(19)  NOT NULL,
    usuario      VARCHAR(40)
);

-- 3.1-A Aviso del día (texto). En H5 se manda al chat simulado y se puede abrir en WhatsApp con wa.me.
CREATE TABLE IF NOT EXISTS avisos (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_conciliacion BIGINT        NOT NULL REFERENCES conciliaciones(id) ON DELETE CASCADE,
    fecha           VARCHAR(19)   NOT NULL,
    asunto          VARCHAR(200)  NOT NULL,
    cuerpo          VARCHAR(4000) NOT NULL
);

-- 3.3 Feedback anónimo (como eventos: sobrevive a «Reiniciar demo»). Nunca información tributaria real.
CREATE TABLE IF NOT EXISTS feedback (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    sesion     VARCHAR(36)  NOT NULL,
    respuesta  VARCHAR(12)  NOT NULL CHECK (respuesta IN ('si','mas_o_menos','no')),
    comentario VARCHAR(500),
    fecha      VARCHAR(19)  NOT NULL
);

-- ===== H5 · Chat simulado tipo WhatsApp: nada sale de la app (el envío real es manual con wa.me) =====
CREATE TABLE IF NOT EXISTS mensajes (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT        NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    direccion  VARCHAR(9)    NOT NULL CHECK (direccion IN ('SALIENTE','ENTRANTE')),
    texto      VARCHAR(4000) NOT NULL,
    fecha      VARCHAR(19)   NOT NULL,
    usuario    VARCHAR(40)
);

-- ===== H5 · Simulador SIRE: tickets de «descarga de propuesta» (no hay conexión con SUNAT) =====
CREATE TABLE IF NOT EXISTS sire_tickets (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT        NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    periodo    VARCHAR(7)    NOT NULL,
    creado     VARCHAR(23)   NOT NULL,
    filas      INT           NOT NULL,
    contenido  CLOB          NOT NULL,
    estado     VARCHAR(20)   NOT NULL CHECK (estado IN ('GENERADO','IMPORTADO','ACEPTADO_SIMULADO'))
);
-- ===== Seguridad (ISO 27001 A.8.15): bitácora de auditoría. Nunca guarda contenido de archivos ni datos personales =====
CREATE TABLE IF NOT EXISTS auditoria (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha   VARCHAR(19)  NOT NULL,
    evento  VARCHAR(30)  NOT NULL,
    detalle VARCHAR(400) NOT NULL
);
