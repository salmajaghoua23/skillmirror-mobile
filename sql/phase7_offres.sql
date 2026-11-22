-- =====================================================================
-- Phase 7 - Offres d'emploi, candidatures, consentement
-- À exécuter UNE FOIS dans Supabase > SQL Editor > New query > Run
-- =====================================================================

-- 1) Offres d'emploi
create table if not exists public.job_offers (
  id               uuid primary key default gen_random_uuid(),
  recruiter_id     uuid not null references public.recruiter_profiles(id) on delete cascade,
  title            text not null,
  description      text not null,
  location         text,
  contract_type    text not null,              -- CDI | CDD | STAGE | ALTERNANCE | FREELANCE
  experience_level text,                       -- JUNIOR | INTERMEDIATE | SENIOR
  remote           boolean not null default false,
  salary_range     text,
  status           text not null default 'DRAFT',   -- DRAFT | PUBLISHED | CLOSED
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);
create index if not exists idx_job_offers_recruiter on public.job_offers(recruiter_id);
create index if not exists idx_job_offers_status    on public.job_offers(status);

-- 2) Compétences demandées par offre
create table if not exists public.job_offer_skills (
  id         uuid primary key default gen_random_uuid(),
  offer_id   uuid not null references public.job_offers(id) on delete cascade,
  skill_name text not null,
  required   boolean not null default true,
  unique (offer_id, skill_name)
);

-- 3) Candidatures
create table if not exists public.applications (
  id                   uuid primary key default gen_random_uuid(),
  offer_id             uuid not null references public.job_offers(id) on delete cascade,
  candidate_profile_id uuid not null references public.profiles(id) on delete cascade,
  status               text not null default 'SENT',  -- SENT | VIEWED | SHORTLISTED | ACCEPTED | REJECTED
  message              text,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now(),
  unique (offer_id, candidate_profile_id)
);
create index if not exists idx_applications_offer     on public.applications(offer_id);
create index if not exists idx_applications_candidate on public.applications(candidate_profile_id);

-- 4) Consentement du candidat (visible par les recruteurs ou non)
alter table public.candidate_profiles
  add column if not exists share_with_recruiters boolean not null default false;

-- 5) Sécurité : seul Spring Boot (compte qui ignore la RLS) lit/écrit ces tables.
alter table public.job_offers       enable row level security;
alter table public.job_offer_skills enable row level security;
alter table public.applications     enable row level security;
