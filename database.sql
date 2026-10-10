--
-- PostgreSQL database dump
--


-- Dumped from database version 18.3 (Homebrew)
-- Dumped by pg_dump version 18.3 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: hstore; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS hstore WITH SCHEMA public;


--
-- Name: EXTENSION hstore; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION hstore IS 'data type for storing sets of (key, value) pairs';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION vector IS 'vector data type and ivfflat and hnsw access methods';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: db_column; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.db_column (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    description text NOT NULL,
    is_primary_key boolean NOT NULL,
    table_id uuid NOT NULL
);


--
-- Name: db_foreign_key; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.db_foreign_key (
    id uuid NOT NULL,
    source_column_id uuid NOT NULL,
    target_column_id uuid NOT NULL
);


--
-- Name: db_table; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.db_table (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description text NOT NULL,
    database_id character varying(255) NOT NULL
);


--
-- Name: glossary_knowledge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.glossary_knowledge (
    id uuid NOT NULL,
    database_id character varying(255) NOT NULL,
    term character varying(255) NOT NULL,
    description text NOT NULL,
    synonyms character varying(255)
);


--
-- Name: question_knowledge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.question_knowledge (
    id uuid NOT NULL,
    database_id character varying(255) NOT NULL,
    question text NOT NULL,
    answer text NOT NULL
);


--
-- Name: vector_store; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vector_store (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    content text,
    metadata json,
    embedding public.vector(512)
);


--
-- Data for Name: db_column; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: db_foreign_key; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: db_table; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: glossary_knowledge; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: question_knowledge; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: vector_store; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: db_column db_column_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.db_column
    ADD CONSTRAINT db_column_pkey PRIMARY KEY (id);


--
-- Name: db_foreign_key db_foreign_key_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.db_foreign_key
    ADD CONSTRAINT db_foreign_key_pkey PRIMARY KEY (id);


--
-- Name: db_table db_table_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.db_table
    ADD CONSTRAINT db_table_pkey PRIMARY KEY (id);


--
-- Name: glossary_knowledge glossary_knowledge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.glossary_knowledge
    ADD CONSTRAINT glossary_knowledge_pkey PRIMARY KEY (id);


--
-- Name: question_knowledge question_knowledge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.question_knowledge
    ADD CONSTRAINT question_knowledge_pkey PRIMARY KEY (id);


--
-- Name: db_column uq_db_column_table_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.db_column
    ADD CONSTRAINT uq_db_column_table_name UNIQUE (table_id, name);


--
-- Name: db_foreign_key uq_db_foreign_key_source_target; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.db_foreign_key
    ADD CONSTRAINT uq_db_foreign_key_source_target UNIQUE (source_column_id, target_column_id);


--
-- Name: db_table uq_db_table_name_database; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.db_table
    ADD CONSTRAINT uq_db_table_name_database UNIQUE (database_id, name);


--
-- Name: vector_store vector_store_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vector_store
    ADD CONSTRAINT vector_store_pkey PRIMARY KEY (id);


--
-- Name: spring_ai_vector_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX spring_ai_vector_index ON public.vector_store USING hnsw (embedding public.vector_cosine_ops);


--
-- Name: db_column fk_db_column_table_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.db_column
    ADD CONSTRAINT fk_db_column_table_id FOREIGN KEY (table_id) REFERENCES public.db_table(id) ON DELETE CASCADE;


--
-- Name: db_foreign_key fk_db_fk_source_column; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.db_foreign_key
    ADD CONSTRAINT fk_db_fk_source_column FOREIGN KEY (source_column_id) REFERENCES public.db_column(id) ON DELETE CASCADE;


--
-- Name: db_foreign_key fk_db_fk_target_column; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.db_foreign_key
    ADD CONSTRAINT fk_db_fk_target_column FOREIGN KEY (target_column_id) REFERENCES public.db_column(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--


