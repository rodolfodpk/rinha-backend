SET row_security = off;

DROP TABLE IF EXISTS public.PESSOAS;

CREATE TABLE public.PESSOAS (
    ID uuid NOT NULL,
    APELIDO VARCHAR(32) UNIQUE NOT NULL,
    NASCIMENTO VARCHAR(12) NOT NULL,
    NOME VARCHAR(100) NOT NULL,
    STACK VARCHAR(255),
    PRIMARY KEY (ID),
    BUSCA_TRGM TEXT GENERATED ALWAYS AS (
      LOWER(NOME || APELIDO || COALESCE(STACK, ''))
    ) STORED NOT NULL
);

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Defina o esquema 'public' novamente antes de criar o índice
SELECT pg_catalog.set_config('search_path', 'public', false);

CREATE INDEX CONCURRENTLY IF NOT EXISTS IDX_PESSOAS_BUSCA_TGRM ON public.PESSOAS
  USING GIST (BUSCA_TRGM GIST_TRGM_OPS(siglen=256))

INCLUDE(apelido, nascimento, nome, ID, stack);

ALTER TABLE public.PESSOAS OWNER TO rinha;
