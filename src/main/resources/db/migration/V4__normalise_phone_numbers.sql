-- V4: phone numbers are stored without spaces, dashes, dots or brackets (see web/Phones.java).
UPDATE users SET phone = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(phone, ' ', ''), '-', ''), '.', ''), '(', ''), ')', '')
    WHERE phone IS NOT NULL;
