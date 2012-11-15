--
--	Copyright (c) 2012 Innova Co SARL. All rights reserved.
--
-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are met:
--      * Redistributions of source code must retain the above copyright
--       notice, this list of conditions and the following disclaimer.
--     * Redistributions in binary form must reproduce the above copyright
--       notice, this list of conditions and the following disclaimer in the
--       documentation and/or other materials provided with the distribution.
--     * Neither the name of the Carbon Foundation X nor the
--       names of its contributors may be used to endorse or promote products
--       derived from this software without specific prior written permission.
--
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
-- ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
-- WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
-- DISCLAIMED. IN NO EVENT SHALL Carbon Foundation X BE LIABLE FOR ANY
-- DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
-- (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
-- LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
-- ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
-- (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS

-- Author(s): Magomed Abdurakhmanov (maga@inn.eu)

set client_min_messages='warning';

----------------------------------------------------------------------------------------
-- Drop database
----------------------------------------------------------------------------------------

drop database if exists codemetrics;

----------------------------------------------------------------------------------------
-- Create roles
----------------------------------------------------------------------------------------

drop role if exists codemetrics_user;
drop role if exists codemetrics;
drop role if exists codemetrics_web_user;
drop role if exists codemetrics_web;
drop role if exists codemetrics_admin_user;
drop role if exists codemetrics_admin;

create role codemetrics inherit valid until 'infinity';
create role codemetrics_user login password '1' valid until 'infinity';
grant codemetrics to codemetrics_user;

create role codemetrics_web inherit valid until 'infinity';
create role codemetrics_web_user login password '1' valid until 'infinity';
grant codemetrics_web to codemetrics_web_user;

create role codemetrics_admin inherit valid until 'infinity';
create role codemetrics_admin_user login password '1' valid until 'infinity';
grant codemetrics_admin to codemetrics_admin_user;

----------------------------------------------------------------------------------------
-- Create database
----------------------------------------------------------------------------------------

create database codemetrics with encoding='UTF8' owner=_postgres;

\c codemetrics
set client_min_messages='warning';

----------------------------------------------------------------------------------------
--	Revoke access from public schema for all users except our roles
----------------------------------------------------------------------------------------

revoke all on schema public from public; -- only admin can create objects within schema
grant usage on schema public to group codemetrics;
grant usage on schema public to group codemetrics_web;
grant usage on schema public to group codemetrics_admin;


-- create domain url_type as varchar(250);

----------------------------------------------------------------------------------------
-- config table
----------------------------------------------------------------------------------------

create table config
(
	name varchar(120) not null,            -- name of config parameter
	val varchar(120) not null,             -- value of config parameter
	constraint pk_config primary key (name)
);

grant select on config to codemetrics, codemetrics_web, codemetrics_admin;

insert into config(name, val) values ('db.version', '0');

----------------------------------------------------------------------------------------
-- project table
----------------------------------------------------------------------------------------

create table project
(
    project_id int,
	name varchar(120) not null,
	path varchar(120) not null,
	is_deleted bool not null default false,
	created timestamptz not null default now(),
    exclude boolean not null default false,
	constraint pk_project primary key (project_id)
);

grant select,insert,update on project to codemetrics, codemetrics_admin;
grant select on project to codemetrics_web;

create unique index ix_project__path on project(path);

create sequence project_id_seq;
alter table project alter column project_id set default nextval('project_id_seq');
alter sequence project_id_seq owned by project.project_id;
grant select, usage on project_id_seq to codemetrics;

----------------------------------------------------------------------------------------
-- file_type (languages)
----------------------------------------------------------------------------------------

create table file_type
(
    file_type_id int,
    name varchar(120) not null,
    exclude boolean not null default false,
    constraint pk_file_type primary key (file_type_id)
);

grant select,insert,update on file_type to codemetrics_admin;
grant select, insert on file_type to codemetrics;
grant select on file_type to codemetrics_web;

create unique index ix_file_type__name on file_type(name);

create sequence file_type_id_seq;
alter table file_type alter column file_type_id set default nextval('file_type_id_seq');
alter sequence file_type_id_seq owned by file_type.file_type_id;
grant select, usage on file_type_id_seq to codemetrics;

----------------------------------------------------------------------------------------
-- project file_category
----------------------------------------------------------------------------------------

create table file_category
(
    file_category_id int,
    project_id int null,
    name varchar(120) not null,
    regex varchar(4096) not null,
    priority int not null,
    diff_handler varchar(120) null,
    cloc_language varchar(120) null,
    exclude boolean not null default false,
   	constraint pk_file_category primary key (file_category_id),
    constraint fk_file_category__project foreign key (project_id) references project(project_id)
);

grant select,insert,update on file_category to codemetrics_admin;
grant select on file_category to codemetrics, codemetrics_web;

create sequence file_category_id_seq;
alter table file_category alter column file_category_id set default nextval('file_category_id_seq');
alter sequence file_category_id_seq owned by file_category.file_category_id;
grant select, usage on file_category_id_seq to codemetrics;

----------------------------------------------------------------------------------------
-- author
----------------------------------------------------------------------------------------

create table author
(
    author_id int,
    name varchar(120) not null,
    constraint pk_author primary key (author_id)
);

grant select,insert,update on author to codemetrics_admin;
grant select,insert on author to codemetrics;
grant select on author to codemetrics_web;

create sequence author_id_seq;
alter table author alter column author_id set default nextval('author_id_seq');
alter sequence author_id_seq owned by author.author_id;
grant select, usage on author_id_seq to codemetrics;

----------------------------------------------------------------------------------------
-- author_alias
----------------------------------------------------------------------------------------

create table author_alias
(
    email varchar(120) not null,
    author_id int,
    constraint pk_author_alias primary key (email),
    constraint fk_author_alias__author foreign key (author_id) references author(author_id)
);

grant select,insert,update on author_alias to codemetrics_admin;
grant select,insert on author_alias to codemetrics;
grant select on author_alias to codemetrics_web;

----------------------------------------------------------------------------------------
-- file
----------------------------------------------------------------------------------------

create table file
(
    file_id int,
    project_id int not null,
    file_type_id int not null,
    file_category_id int null,
    path varchar(4096) not null,
    constraint pk_file primary key (file_id),
    constraint fk_file__project foreign key (project_id) references project(project_id),
    constraint fk_file__file_type foreign key (file_type_id) references file_type(file_type_id),
    constraint fk_file__file_category foreign key (file_category_id) references file_category(file_category_id)
);

grant select,insert,update,delete on file to codemetrics_admin;
grant select,insert,update,delete on file to codemetrics;
grant select on file to codemetrics_web;

create sequence file_id_seq;
alter table file alter column file_id set default nextval('file_id_seq');
alter sequence file_id_seq owned by file.file_id;
grant select, usage on file_id_seq to codemetrics;

----------------------------------------------------------------------------------------
-- commt (commit)
----------------------------------------------------------------------------------------

create table commt
(
    commt_id bigint not null,
    hash varchar(120) not null,
    project_id int not null,
    author_id int not null,
    commt_type smallint not null,
    dt timestamptz not null,
    exclude boolean not null default false,
    processed boolean not null default false,
    constraint pk_commt primary key (commt_id),
    constraint fk_commt__project foreign key (project_id) references project(project_id),
    constraint fk_commt__author foreign key (author_id) references author(author_id),
    constraint ck_commt__commt_type check (commt_type in (1 /*normal*/, 2 /*merge*/))
);

create unique index ix_commt_hash on commt(hash);

grant select,insert,update,delete on commt to codemetrics_admin;
grant select,insert,update,delete on commt to codemetrics;
grant select on commt to codemetrics_web;

create sequence commt_id_seq;
alter table commt alter column commt_id set default nextval('commt_id_seq');
alter sequence commt_id_seq owned by commt.commt_id;
grant select, usage on commt_id_seq to codemetrics;

----------------------------------------------------------------------------------------
-- metric_type
----------------------------------------------------------------------------------------

create table metric_type
(
    metric_type_id int not null,
    name varchar(120) not null,
    constraint pk_metric_type primary key (metric_type_id)
);

create unique index ix_metric_type__name on metric_type(name);

grant select,insert,update,delete on metric_type to codemetrics_admin;
grant select,insert on metric_type to codemetrics;
grant select on metric_type to codemetrics_web;

create sequence metric_type_id_seq;
alter table metric_type alter column metric_type_id set default nextval('metric_type_id_seq');
alter sequence metric_type_id_seq owned by metric_type.metric_type_id;
grant select, usage on metric_type_id_seq to codemetrics;

----------------------------------------------------------------------------------------
-- metric
----------------------------------------------------------------------------------------

create table metric
(
    commt_id bigint not null,
    metric_type_id int not null,
    file_id int not null,
    value int not null,
    constraint fk_metric__commt foreign key (commt_id) references commt(commt_id),
    constraint fk_metric__metric_type foreign key (metric_type_id) references metric_type(metric_type_id),
    constraint fk_metric__file foreign key (file_id) references file(file_id)
);

grant select,insert,update,delete on metric to codemetrics_admin;
grant select,insert,update,delete on metric to codemetrics;
grant select on metric to codemetrics_web;

----------------------------------------------------------------------------------------
-- file_version
----------------------------------------------------------------------------------------

create table file_version
(
    file_version_id bigint not null,
    commt_id bigint not null,
    file_id int not null,
    similar_file_version_id bigint null,
    similarity float4 not null,
    nonws_md5 bytea not null,
    is_new bool not null,
    constraint pk_file_version primary key (file_version_id),
    constraint fk_file_version__commt foreign key (commt_id) references commt(commt_id),
    constraint fk_file_version__file foreign key (file_id) references file(file_id),
    constraint fk_file_version__similar foreign key (similar_file_version_id) references file_version(file_version_id)
);

create sequence file_version_id_seq;
alter table file_version alter column file_version_id set default nextval('file_version_id_seq');
alter sequence file_version_id_seq owned by file_version.file_version_id;
grant select, usage on file_version_id_seq to codemetrics;

grant select,insert,update,delete on file_version to codemetrics_admin;
grant select,insert,update,delete on file_version to codemetrics;
grant select on file_version to codemetrics_web;

create index ix_file_version__nonws_md5 on file_version(nonws_md5);

----------------------------------------------------------------------------------------
-- fingerprint
----------------------------------------------------------------------------------------

create table fingerprint
(
    file_version_id bigint not null,
    type char not null,
    key smallint not null,
    value int not null,
    line_count int not null,
    constraint fk_fingerprint__file_version foreign key (file_version_id) references file_version(file_version_id)
);

grant select,insert,update,delete on fingerprint to codemetrics_admin;
grant select,insert,update,delete on fingerprint to codemetrics;
grant select on fingerprint to codemetrics_web;

create index ix_fingerprint__tkv on fingerprint(type,key,value);



