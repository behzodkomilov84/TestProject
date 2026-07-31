--liquibase formatted sql

--changeset behzod:23
CREATE TABLE user_roles (
                             user_id BIGINT NOT NULL,
                             role_id BIGINT NOT NULL,
                             PRIMARY KEY (user_id, role_id),
                             CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
                                 REFERENCES users(id)
                                 ON DELETE CASCADE,
                             CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id)
                                 REFERENCES roles(id)
                                 ON DELETE CASCADE
);

--changeset behzod:24
-- Mavjud users.role_id ustunidagi ma'lumotni yangi user_roles jadvaliga ko'chiramiz,
-- shunda hech kim rolini yo'qotmaydi.
INSERT INTO user_roles (user_id, role_id)
SELECT id, role_id FROM users WHERE role_id IS NOT NULL;

--changeset behzod:25
ALTER TABLE users DROP FOREIGN KEY fk_user_role;

--changeset behzod:26
ALTER TABLE users DROP COLUMN role_id;
