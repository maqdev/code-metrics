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

-- Author(s): Magomed Abdurakhmanov (maqdev@gmail.com)

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
    project_id serial,
	name varchar(120) not null,
	path varchar(120) not null,
	is_deleted bool not null default false,
	created timestamptz not null default now(),
	constraint pk_project primary key (project_id)
);

grant select,insert,update on project to codemetrics, codemetrics_admin;
grant select on project to codemetrics_web;

----------------------------------------------------------------------------------------
-- project file groups
----------------------------------------------------------------------------------------

create table file_group
(
    file_group_id serial,
    project_id int not null,
    name varchar(120) not null,
    mask varchar(120) not null,
    priority int not null,
   	constraint pk_file_group primary key (file_group_id),
    constraint fk_file_group__project foreign key (project_id) references project(project_id)
);

grant select,insert,update on file_group to codemetrics_admin;
grant select on file_group to codemetrics, codemetrics_web;

----------------------------------------------------------------------------------------
-- file types
----------------------------------------------------------------------------------------

create table file_type
(
    file_type_id serial,
    name varchar(120) not null,
    mask varchar(4096) not null,
    constraint pk_file_type primary key (file_type_id)
);

grant select,insert,update on file_type to codemetrics_admin;
grant select, insert on file_type to codemetrics;
grant select on file_type to codemetrics_web;

----------------------------------------------------------------------------------------
-- author
----------------------------------------------------------------------------------------

create table author
(
    author_id serial,
    name varchar(120) not null,
    email varchar(120) not null,
    aliases varchar(4096) not null,
    constraint pk_author primary key (author_id)
);

grant select,insert,update on author to codemetrics_admin;
grant select, insert on author to codemetrics;
grant select on author to codemetrics_web;

----------------------------------------------------------------------------------------
-- file
----------------------------------------------------------------------------------------

create table file
(
    file_id serial,
    project_id int not null,
    file_type_id int not null,
    path varchar(4096) not null,
    constraint pk_file primary key (file_id),
    constraint fk_file__project foreign key (project_id) references project(project_id),
    constraint fk_file__file_type foreign key (file_type_id) references file_type(file_type_id)
);

grant select,insert,update,delete on file to codemetrics_admin;
grant select, insert on file to codemetrics;
grant select on file to codemetrics_web;

----------------------------------------------------------------------------------------
-- commt (commit)
----------------------------------------------------------------------------------------

create table commt
(
    commt_id bigserial not null,
    hash varchar(120) not null,
    project_id int not null,
    author_id int not null,
    dt timestamptz not null,
    constraint pk_commt primary key (commt_id),
    constraint fk_commt__project foreign key (project_id) references project(project_id),
    constraint fk_commt__author foreign key (author_id) references author(author_id)
);

create unique index ix_commt_hash on commt(hash);

grant select,insert,update,delete on commt to codemetrics_admin;
grant select,insert,update on commt to codemetrics;
grant select on commt to codemetrics_web;

----------------------------------------------------------------------------------------
-- metric_type
----------------------------------------------------------------------------------------

create table metric_type
(
    metric_type_id smallint not null,
    name varchar(120) not null,
    constraint pk_metric_type primary key (metric_type_id)
);

grant select,insert,update,delete on metric_type to codemetrics_admin;
grant select on metric_type to codemetrics;
grant select on metric_type to codemetrics_web;

----------------------------------------------------------------------------------------
-- metric
----------------------------------------------------------------------------------------

create table metric
(
    commt_id bigint not null,
    metric_type_id smallint not null,
    value int not null,
    constraint fk_metric__commt foreign key (commt_id) references commt(commt_id),
    constraint fk_metric__metric_type foreign key (metric_type_id) references metric_type(metric_type_id)
);

grant select,insert,update,delete on metric to codemetrics_admin;
grant select,insert,update,delete on metric to codemetrics;
grant select on metric to codemetrics_web;
