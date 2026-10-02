-- How the watch recorded a session (docs/DECISIONS.md, Smart recording support): EVERY_SECOND or SMART.
alter table session
    add column recording_mode text not null default 'EVERY_SECOND'
        check (recording_mode in ('EVERY_SECOND', 'SMART'));
